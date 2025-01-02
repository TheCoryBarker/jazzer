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

package com.code_intelligence.jazzer.runtime;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import sun.misc.Unsafe;

/**
 * Stub to be used during offline instrumentation to avoid jni issues that can pop up
 */
final public class CoverageMap {
  @SuppressWarnings("unused")
  public static void enlargeIfNeeded(int nextId) {}

  @SuppressWarnings("unused")
  public static void recordCoverage(final int id) {}

  public static Set<Integer> getCoveredIds() {
    return new HashSet<>();
  }

  public static void replayCoveredIds(Set<Integer> coveredIds) {}

  public static int[] getEverCoveredIds() {
    return new int[1];
  }

  private static void initialize(long countersAddress) {}

  private static void registerNewCounters(int oldNumCounters, int newNumCounters) {}
}
