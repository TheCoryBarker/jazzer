// Copyright 2022 Code Intelligence GmbH
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

package com.code_intelligence.jazzer.agent;

import java.io.File;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;
import java.util.jar.JarFile;

import net.bytebuddy.agent.ByteBuddyAgent;

public class AgentInstaller {
  private static final AtomicBoolean hasBeenInstalled = new AtomicBoolean();
  private static final Logger logger = Logger.getLogger(AgentInstaller.class.getName());
  /**
   * Installs the Jazzer agent for build-time instrumentation.
   * For Android builds, we only instrument application code (not bootstrap classes),
   * so there's no need to add anything to the bootstrap classloader.
   */
  public static void install(boolean enableAgent) {
    // Only install the agent once.
    if (!hasBeenInstalled.compareAndSet(false, true)) {
      return;
    }

    Instrumentation instrumentation = ByteBuddyAgent.install();

    // For Android build-time instrumentation, we don't need to add anything to bootstrap
    // classloader since we're only instrumenting application code, not bootstrap classes.
    // Sanitizers are provided as dependencies in the AOSP build and are already on the
    // classpath.

    if (!enableAgent) {
      return;
    }

    try {
      Class<?> agent = Class.forName("com.code_intelligence.jazzer.agent.Agent");
      Method install = agent.getMethod("install", Instrumentation.class);
      install.invoke(null, instrumentation);
    } catch (ClassNotFoundException | InvocationTargetException | NoSuchMethodException
        | IllegalAccessException e) {
      throw new IllegalStateException("Failed to run Agent.install", e);
    }
  }

  /**
   * Installs the Jazzer agent for build-time instrumentation and appends the given jars to the
   * bootstrap classloader search so that hook classes contained in those jars are resolvable
   * during hook discovery.
   */
  public static void installWithHookJars(List<String> hookJars) {
    if (!hasBeenInstalled.compareAndSet(false, true)) {
      return;
    }

    Instrumentation instrumentation = ByteBuddyAgent.install();

    if (hookJars != null) {
      for (String jarPath : hookJars) {
        try {
          instrumentation.appendToBootstrapClassLoaderSearch(new JarFile(jarPath));
        } catch (Throwable t) {
          logger.warning("Failed to append to bootstrap: " + jarPath + " - " + t);
        }
      }
    }

    try {
      Class<?> agent = Class.forName("com.code_intelligence.jazzer.agent.Agent");
      Method install = agent.getMethod("install", Instrumentation.class);
      install.invoke(null, instrumentation);
    } catch (ClassNotFoundException | InvocationTargetException | NoSuchMethodException
        | IllegalAccessException e) {
      throw new IllegalStateException("Failed to run Agent.install", e);
    }
  }
}
