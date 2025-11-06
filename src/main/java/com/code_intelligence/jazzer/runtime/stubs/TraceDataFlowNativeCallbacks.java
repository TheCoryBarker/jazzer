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

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.nio.charset.Charset;
import java.util.Arrays;

@SuppressWarnings("unused")
final public class TraceDataFlowNativeCallbacks {
  public static native void traceMemcmp(byte[] b1, byte[] b2, int result, int pc);

  public static void traceStrcmp(String s1, String s2, int result, int pc) {}

  public static void traceStrstr(String s1, String s2, int pc) {}

  public static void traceReflectiveCall(Executable callee, int pc) {}

  public static int traceCmpLongWrapper(long arg1, long arg2, int pc) {
    return 0;
  }

  // The caller has to ensure that arg1 and arg2 have the same class.
  public static void traceGenericCmp(Object arg1, Object arg2, int pc) {}

  /* trace-cmp */
  public static void traceCmpInt(int arg1, int arg2, int pc) {}
  public static void traceConstCmpInt(int arg1, int arg2, int pc) {}
  public static void traceCmpLong(long arg1, long arg2, int pc) {}
  public static void traceSwitch(long val, long[] cases, int pc) {}
  /* trace-div */
  public static void traceDivInt(int val, int pc) {}
  public static void traceDivLong(long val, int pc) {}
  /* trace-gep */
  public static void traceGep(long val, int pc) {}
  /* indirect-calls */
  public static void tracePcIndir(int callee, int caller) {}

  public static void handleLibraryLoad() {}

  private static byte[] encodeForLibFuzzer(String str) {
    return new byte[1];
  }

  private static void traceStrstr0(byte[] needle, int pc) {}
}
