// Copyright 2021 Code Intelligence GmbH
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.code_intelligence.jazzer.driver;

import com.code_intelligence.jazzer.agent.AgentInstaller;
import com.code_intelligence.jazzer.driver.Opt;
import com.code_intelligence.jazzer.utils.Log;
import com.code_intelligence.jazzer.utils.ZipUtils;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.UnsupportedClassVersionError;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipOutputStream;

public class OfflineInstrumentor {
  public static boolean instrumentJars(List<String> jarList, boolean addNativeLibs)
      throws IOException {
    AgentInstaller.install(true);
    // TODO: as a working proof of concept, this has only been tested on one jar.
    // This should be fine for most scenarios since this should be happening directly
    // before dexing.

    // Clear instrumented dir before adding new instrumented classes
    File dumpClassesDir = new File(Opt.dumpClassesDir.get());
    dumpClassesDir.deleteOnExit();
    if (dumpClassesDir.exists()) {
      for (String fn : dumpClassesDir.list()) {
        new File(Opt.dumpClassesDir.get(), fn).delete();
      }
    }

    // extract bootstrap.jar & jazzer.jar
    File jazzerForAndroid =
        ZipUtils.extractFileFromJar("/com/code_intelligence/jazzer/android/jazzer_android.jar");
    jazzerForAndroid.deleteOnExit();

    File bootstrapJar = Files.createTempFile("bootstrap", ".jar").toFile();
    ZipUtils.extractFileFromJar(jazzerForAndroid.getPath(),
        "com/code_intelligence/jazzer/runtime/jazzer_bootstrap.jar", bootstrapJar.getPath());
    bootstrapJar.deleteOnExit();

    File jazzerRuntimeJar = Files.createTempFile("jazzerRuntime", ".jar").toFile();
    ZipUtils.extractFileFromJar(jazzerForAndroid.getPath(),
        "com/code_intelligence/jazzer/jazzer.jar", jazzerRuntimeJar.getPath());
    jazzerRuntimeJar.deleteOnExit();

    // instrument all jars and replace original with instrumented version
    for (String jarpath : jarList) {
      File jar = processSingleJar(
          jarpath, jazzerForAndroid, jazzerRuntimeJar, bootstrapJar, addNativeLibs);
      Files.copy(jar.toPath(), Paths.get(jarpath), StandardCopyOption.REPLACE_EXISTING);
    }

    return true;
  }

  private static File processSingleJar(String jar, File androidJazzerJar, File jazzerRuntimeJar,
      File bootstrapJar, boolean addNativeLibs) throws IOException {
    // Instrument classes and output new file in output dir
    createInstrumentedClasses(jar);

    Path nativeLibsDir = Paths.get(Opt.dumpClassesDir.get() + File.separator + "lib"
        + File.separator + "arm64-v8a" + File.separator);
    new File(nativeLibsDir.toString()).mkdirs();

    if (addNativeLibs) {
      Path jazzerDriverSO = Files.createFile(
          Paths.get(nativeLibsDir.toString() + File.separator + "libjazzer_driver.so"));
      ZipUtils.extractFileFromJar(jazzerRuntimeJar.getPath(),
          "com/code_intelligence/jazzer/driver/jazzer_driver_android_aarch64/libjazzer_driver.so",
          jazzerDriverSO.toString());

      Path jazzerPreloadSO = Files.createFile(
          Paths.get(nativeLibsDir.toString() + File.separator + "libjazzer_preload.so"));
      ZipUtils.extractFileFromJar(jazzerRuntimeJar.getPath(),
          "com/code_intelligence/jazzer/jazzer_preload_android_aarch64/libjazzer_preload.so",
          jazzerPreloadSO.toString());

      Path fuzzedDataProviderSO = Files.createFile(Paths.get(
          nativeLibsDir.toString() + File.separator + "libjazzer_fuzzed_data_provider.so"));
      ZipUtils.extractFileFromJar(jazzerRuntimeJar.getPath(),
          "com/code_intelligence/jazzer/driver/jazzer_fuzzed_data_provider_android_aarch64/libjazzer_fuzzed_data_provider.so",
          fuzzedDataProviderSO.toString());
    }

    // Copy jazzer files to directory
    // Contains Android specific startup code
    //ZipUtils.mergeJarToDirectory(androidJazzerJar.getPath(), Opt.dumpClassesDir.get());
    // Contains Jazzer runtime classes
    //ZipUtils.mergeJarToDirectory(jazzerRuntimeJar.getPath(), Opt.dumpClassesDir.get());
    // Contains classes that will need to be injected into bootstrap if bootstrap class
    // instrumentation is to be supported.
    //ZipUtils.mergeJarToDirectory(bootstrapJar.getPath(), Opt.dumpClassesDir.get());
    // Merge all class files that are not newly instrumented class files into output dir
    ZipUtils.mergeJarToDirectory(jar, Opt.dumpClassesDir.get());

    // Get manifest for new jar, and create new jar from output dir
    Manifest manifest = ZipUtils.getManifest(jar);
    File tempJar = ZipUtils.directoryToJar(new File(Opt.dumpClassesDir.get()), manifest);

    return tempJar;
  }

  /**
   * Loops over all classes in jar file and adds instrumentation. The output
   * of the instrumented classes will be at --dump-classes-dir
   *
   * @param jarPath a path to a jar file to instrument.
   * @return a list of errors that were hit when trying to instrument all classes in jar
   */
  private static void createInstrumentedClasses(String jarPath) throws IOException {
    List<String> allClasses = new ArrayList<>();

    // Collect all classes for jar file
    try (JarFile jarFile = new JarFile(jarPath)) {
      Enumeration<JarEntry> allEntries = jarFile.entries();
      while (allEntries.hasMoreElements()) {
        JarEntry entry = allEntries.nextElement();
        if (entry.isDirectory()) {
          continue;
        }

        String name = entry.getName();
        if (!name.endsWith(".class")) {
          Log.info("Skipping instrumenting file: " + name);
          continue;
        }

        String className = name.substring(0, name.lastIndexOf(".class"));
        className = className.replace('/', '.');
        allClasses.add(className);
        //Log.info("Found class: " + className);
      }
    }

    // No classes found, so none to load. Return errors
    if (allClasses.size() == 0) {
      Log.warn("Classes is empty for jar: " + jarPath);
      return;
    }

    // Create class loader to load in all classes
    File file = new File(jarPath);
    URL url = file.toURI().toURL();
    URL[] urls = new URL[] {url};
    ClassLoader cl = new URLClassLoader(urls);

    // Loop through all files and load in all classes, agent will instrument them as they load
    for (String className : allClasses) {
      try {
        cl.loadClass(className);
      } catch (UnsupportedClassVersionError ucve) {
        // The classes will still get instrumented here, but warn so the user knows something
        // happened
        Log.warn(ucve.toString());
      } catch (Throwable e) {
        // Catch all exceptions/errors and keep instrumenting to give user the option to manually
        // fix one offs if possible
        Log.warn("Failed to instrument class: " + className + ". Error: " + e.toString());
      }
    }

    // clean up .original.class and .failed.class
    Path instClassesDir = Paths.get(Opt.dumpClassesDir.get());
    Files.walkFileTree(instClassesDir, new SimpleFileVisitor<Path>() {
      public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
        String relPath = instClassesDir.relativize(file).toString();
        boolean exists = ZipUtils.fileExistsInJar(jarPath, relPath);
        if (!exists) {
          // Remove from output dir. This is not necessarily needed, but removes packaging up
          // bootstrap classes into our new jar.
          file.toFile().delete();
        }

        return FileVisitResult.CONTINUE;
      }
    });
  }
}
