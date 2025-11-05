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

package com.code_intelligence.jazzer.runtime;

import com.github.fmeum.rules_jni.RulesJni;
import static com.code_intelligence.jazzer.runtime.Constants.IS_ANDROID;
public final class Mutator {
  public static final boolean SHOULD_MOCK =
      Boolean.parseBoolean(System.getenv("JAZZER_MOCK_LIBFUZZER_MUTATOR"));

  static {
    if (!SHOULD_MOCK) {
      if (!IS_ANDROID) {
        RulesJni.loadLibrary("jazzer_driver", Mutator.class);
      } else {
        System.loadLibrary("jazzer_driver");
      }
    }
  }

  public static native int defaultMutateNative(byte[] buffer, int size);

  private Mutator() {}
}
