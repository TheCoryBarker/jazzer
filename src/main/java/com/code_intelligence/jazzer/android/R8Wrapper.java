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

import main.java.com.code_intelligence.jazzer.android.InstrumentationConfig;

import com.code_intelligence.jazzer.driver.OfflineInstrumentor;
import com.code_intelligence.jazzer.driver.Opt;
import java.io.File;
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

  private static void setOptions() throws Exception {
    List<String> jazzerOpts = new ArrayList<>();
    // default config object will have all the default hooks disabled
    InstrumentationConfig config = new InstrumentationConfig();

    InputStream input = R8Wrapper.class
        .getClassLoader()
        .getResourceAsStream("com/code_intelligence/jazzer/android/jazzer_instrumentation_config.json");

    if (input != null) {
      try (InputStream in = input) {
        config.updateFromJson(in);
      }
    } else {
      logger.info("No instrumentation config found — using default config.");
    }

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

      // found com.android.tools.r8warpper.R8Wrapper
      // don't add native libs, we are in AOSP and Soong has special code for this
      boolean instrumentationSuccess = OfflineInstrumentor.instrumentJars(jarfiles, false);
      if (!instrumentationSuccess) {
        exit(1);
      }

      main.invokeExact(args);
      return;
    } catch (ClassNotFoundException cnfe) {
      // This is ok, we wouldn't expect this class to be found outside of AOSP
      logger.warning("No wrapper function found");
    }

    try {
      Class<?> r8 = Class.forName("com.android.tools.r8.R8", false, R8Wrapper.class.getClassLoader());
      MethodHandle main = MethodHandles.lookup().findStatic(
          r8, "main", MethodType.methodType(void.class, String[].class));

      // Calling normal R8 functions.
      // TODO: this path needs more testing
      boolean instrumentationSuccess = OfflineInstrumentor.instrumentJars(jarfiles, true);
      if (!instrumentationSuccess) {
        exit(1);
      }

      main.invokeExact(args);
    } catch (Exception e) {
      logger.warning(e);
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
