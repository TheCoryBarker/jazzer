/*
 * Copyright 2023 Code Intelligence GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.code_intelligence.jazzer.utils;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.IllegalArgumentException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Stack;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public final class ZipUtils {
  private ZipUtils() {}

  public static String getJarName(String jarPath) {
    int lastSeparatorIndex = jarPath.lastIndexOf(File.separator);
    if (lastSeparatorIndex != -1) {
      jarPath = jarPath.substring(lastSeparatorIndex + 1);
    }

    jarPath = jarPath.substring(0, jarPath.lastIndexOf(".jar"));
    return jarPath;
  }

  public static void mergeJarToDirectory(String jarPath, String destinationDir) throws IOException {
    File destDir = new File(destinationDir);
    if (!destDir.exists()) {
      destDir.mkdirs();
    }

    try (JarFile jarFile = new JarFile(jarPath)) {
      Enumeration<? extends JarEntry> entries = jarFile.entries();
      while (entries.hasMoreElements()) {
        JarEntry entry = entries.nextElement();
        File destinationFile = new File(destinationDir, entry.getName());

        if (destinationFile.exists()) {
          continue;
        }

        // TODO: create skip file regex instead of this hardcoding
        if (entry.getName().equals("META-INF/MANIFEST.MF")) {
          continue;
        }

        if (entry.getName().endsWith(".so")) {
          continue;
        }

        if (entry.getName().endsWith(".jar")) {
          continue;
        }

        destinationFile.getParentFile().mkdirs();

        if (!entry.isDirectory()) {
          try (BufferedInputStream bis = new BufferedInputStream(jarFile.getInputStream(entry));
               FileOutputStream fos = new FileOutputStream(destinationFile);
               BufferedOutputStream bos = new BufferedOutputStream(fos, 1024)) {
            byte[] buffer = new byte[1024];
            int len;

            while ((len = bis.read(buffer)) > 0) {
              bos.write(buffer, 0, len);
            }
          }
        }
      }
    }
  }

  public static void extractFileFromJar(String jarPath, String targetFile, String destinationPath)
      throws IOException {
    try (JarFile jarFile = new JarFile(jarPath)) {
      ZipEntry entry = jarFile.getEntry(targetFile);
      if (entry != null) {
        File destinationFile = new File(destinationPath);
        try (InputStream is = jarFile.getInputStream(entry);
             FileOutputStream fos = new FileOutputStream(destinationFile)) {
          byte[] buffer = new byte[1024];
          int len;
          while ((len = is.read(buffer)) > 0) {
            fos.write(buffer, 0, len);
          }
          System.out.println("File extracted to: " + destinationPath);
        }
      } else {
        System.out.println("File does not exist in JAR");
      }
    }
  }

  public static File extractFileFromJar(String target) {
    try (InputStream stream = ZipUtils.class.getResourceAsStream(target)) {
      if (stream == null) {
        throw new IllegalStateException(
            String.format("Failed to find jar %s in resources.", target));
      }

      String jarName = getJarName(target);
      File jar = Files.createTempFile(jarName, ".jar").toFile();
      jar.deleteOnExit();
      Files.copy(stream, jar.toPath(), StandardCopyOption.REPLACE_EXISTING);

      return jar;
    } catch (IOException e) {
      throw new IllegalStateException(
          String.format("Failed to find jar %s in resources.", target), e);
    }
  }

  public static Manifest getManifest(String jarPath) throws IOException {
    try (JarFile jarFile = new JarFile(jarPath)) {
      ZipEntry entry = jarFile.getEntry("META-INF/MANIFEST.MF");
      if (entry != null) {
        try (InputStream is = jarFile.getInputStream(entry)) {
          return new Manifest(is);
        }
      } else {
        System.out.println("Could not find manifest");
      }
    }

    return null;
  }

  public static File directoryToJar(File inputDirectory, Manifest manifest) throws IOException {
    File outputjar = Files.createTempFile("instrumentedjar", ".jar").toFile();
    outputjar.deleteOnExit();

    FileOutputStream fos = new FileOutputStream(outputjar.getPath().toString());
    try (JarOutputStream jos = new JarOutputStream(fos, manifest)) {
      addDirectoryToJar(inputDirectory, jos);
    }

    return outputjar;
  }

  private static void addDirectoryToJar(File directory, JarOutputStream jos) throws IOException {
    File[] files = directory.listFiles();
    byte[] buffer = new byte[1024];
    String baseDir = directory.getPath();

    Stack<File> directories = new Stack<>();
    directories.push(directory);

    while (!directories.isEmpty()) {
      File currentDir = directories.pop();
      files = currentDir.listFiles();

      for (File file : files) {
        if (file.isDirectory()) {
          directories.push(file);
        } else {
          String entryName = file.getPath().substring(baseDir.length() + 1);
          JarEntry entry = new JarEntry(entryName);
          jos.putNextEntry(entry);

          try (FileInputStream in = new FileInputStream(file)) {
            int len;
            while ((len = in.read(buffer)) > 0) {
              jos.write(buffer, 0, len);
            }
          }
          jos.closeEntry();
        }
      }
    }
  }

  public static boolean fileExistsInJar(String jarPath, String filePath) {
    try (JarFile zipFile = new JarFile(jarPath)) {
      ZipEntry entry = zipFile.getEntry(filePath);
      return entry != null;
    } catch (IOException e) {
      System.out.println("Error: " + e.getMessage());
      return false;
    }
  }
}
