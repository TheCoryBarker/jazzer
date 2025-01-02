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

package com.code_intelligence.jazzer.android;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.List;

public class AndroidStartWrapper {
  public static int Call(List<String> args) {
    try {
      Class<?> driver = Class.forName("com.code_intelligence.jazzer.driver.Driver", false,
          AndroidStartWrapper.class.getClassLoader());

      MethodHandle start = MethodHandles.lookup().findStatic(
          driver, "start", MethodType.methodType(int.class, List.class, boolean.class));

      return (int) start.invokeExact(args, false);
    } catch (Throwable t) {
      // TODO fix logging
      System.out.println(t);
    }

    return 1;
  }
}