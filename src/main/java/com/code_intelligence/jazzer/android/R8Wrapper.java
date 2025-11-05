/*
 * Copyright 2024 Code Intelligence GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.code_intelligence.jazzer.r8;

import static java.lang.System.exit;

import com.code_intelligence.jazzer.android.InstrumentationConfig;
import com.code_intelligence.jazzer.driver.BuildInstrumentor;
import com.code_intelligence.jazzer.driver.Opt;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ClassNotFoundException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class R8Wrapper {
  private static final Logger logger = Logger.getLogger(R8Wrapper.class.getName());

  // Path to the instrumentation config file on the host.
  // It is relative to the current working directory.
  private static final String CONFIG_FILE_PATH = "prebuilts/jazzer/jazzer_instrumentation_config.json";

  private static void setOptions() throws Exception {
    List<String> jazzerOpts = new ArrayList<>();
    InstrumentationConfig config = new InstrumentationConfig();

    File configFile = new File(CONFIG_FILE_PATH);

    if (configFile.exists()) {
      try (FileInputStream fis = new FileInputStream(configFile)) {
        config.updateFromJson(fis);
      }
    } else {
      logger.info("No instrumentation config found — using default config.");
    }

    jazzerOpts.add("--trace=cov:cmp:div");
    config.addToJazzerOpts(jazzerOpts);
    Opt.registerAndValidateCommandLineArgs(Opt.parseJazzerArgs(jazzerOpts));
  }

  public static void main(String[] args) throws Throwable {
    R8Wrapper.setOptions();
    List<String> jarfiles = R8Wrapper.parseJarFile(args);

    try {
      Class<?> soongR8Wrapper = Class.forName(
          "com.android.tools.r8wrappers.R8Wrapper", false, R8Wrapper.class.getClassLoader());
      MethodHandle main = MethodHandles.lookup().findStatic(
          soongR8Wrapper, "main", MethodType.methodType(void.class, String[].class));

      // Instrument application JARs with Jazzer hooks
      // Native libraries are handled separately by Soong
      boolean instrumentationSuccess = BuildInstrumentor.instrumentJars(jarfiles);
      if (!instrumentationSuccess) {
        exit(1);
      }

      main.invokeExact(args);
    } catch (ClassNotFoundException cnfe) {
      logger.severe("com.android.tools.r8wrappers.R8Wrapper not found - this tool is AOSP-only");
      exit(1);
    } catch (Exception e) {
      logger.severe("Error during R8 processing: " + e.getMessage());
      exit(1);
    }
  }

  private static List<String> parseJarFile(String[] args) {
    List<String> jarlist = new ArrayList<String>();

    for (int i = 0; i < args.length; i++) {
      if (args[i].equals("-injars")) {
        i += 1;
        while (i < args.length) {
          if (args[i].startsWith("-")) {
            // next flag, break;
            break;
          }

          jarlist.add(args[i]);
          i += 1;
        }
      }
    }
    return jarlist;
  }
}
