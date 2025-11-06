/*
 * Copyright 2025 Code Intelligence GmbH
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

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * InstrumentationConfig
 *
 * This class is responsible for parsing and holding configuration options for
 * build-time instrumentation in Jazzer for Android. The configuration is specified
 * at compile time from a JSON file.
 *
 * Example JSON configuration:
 *
 * {
 *   "enabled_hooks": [
 *     "com.code_intelligence.jazzer.sanitizers.IntentRedirection",
 *     "com.code_intelligence.jazzer.sanitizers.Deserialization",
 *     "com.code_intelligence.jazzer.sanitizers.OsCommandInjection"
 *   ],
 *   "instrumentation_includes": [
 *     "com.example.**"
 *   ],
 *   "instrumentation_excludes": [
 *     "com.example.test.**"
 *   ]
 * }
 *
 * The configuration file supports the following options:
 *
 * ### `enabled_hooks`
 * An array of fully-qualified class names of sanitizer/hook classes to enable
 * during instrumentation. These hook classes must be available on the classpath
 * (typically as dependencies of the fuzzer target in AOSP). Only the hooks listed
 * here will be active during fuzzing.
 *
 * ### `instrumentation_includes`
 * An array of wildcard patterns (e.g., "com.example.**") specifying which classes
 * are eligible for instrumentation. This typically includes the application code
 * being fuzzed.
 *
 * ### `instrumentation_excludes`
 * An array of wildcard patterns specifying which classes should be excluded from
 * instrumentation, even if they match the include patterns. This is useful for
 * excluding test code or third-party libraries.
 */
public class InstrumentationConfig {

    private final Logger logger = Logger.getLogger(InstrumentationConfig.class.getName());

    private final String ENABLED_HOOKS = "enabled_hooks";
    private final String INSTRUMENTATION_INCLUDES = "instrumentation_includes";
    private final String INSTRUMENTATION_EXCLUDES = "instrumentation_excludes";

    private final List<String> enabledHooks = new ArrayList<>();
    private final List<String> instrumentationIncludes = new ArrayList<>();
    private final List<String> instrumentationExcludes = new ArrayList<>();
    private final Path dumpClassesDir;

    public InstrumentationConfig() {
        try {
            dumpClassesDir = Files.createTempDirectory("instrumented_classes");
        } catch (IOException e) {
            throw new RuntimeException("Failed to create dump_classes_dir", e);
        }
    }

    public void updateFromJson(InputStream input) throws Exception {
        try (InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();

            for (String key : json.keySet()) {
                switch (key) {
                    case ENABLED_HOOKS:
                        parseEnabledHooks(json.getAsJsonArray(key));
                        break;
                    case INSTRUMENTATION_INCLUDES:
                        parseStringArray(json.getAsJsonArray(key), instrumentationIncludes);
                        break;
                    case INSTRUMENTATION_EXCLUDES:
                        parseStringArray(json.getAsJsonArray(key), instrumentationExcludes);
                        break;
                    default:
                        logger.warning("Unsupported top-level config entry: " + key);
                }
            }

        } catch (Exception e) {
            logger.warning("Failed to load instrumentation config.");
            throw e;
        }
    }

    public void addToJazzerOpts(List<String> jazzerOpts) {
        addOptionIfNotEmpty(enabledHooks, "--custom_hooks=", jazzerOpts);
        addOptionIfNotEmpty(instrumentationIncludes, "--instrumentation_includes=", jazzerOpts);
        addOptionIfNotEmpty(instrumentationExcludes, "--instrumentation_excludes=", jazzerOpts);
        jazzerOpts.add("--dump_classes_dir=" + dumpClassesDir.toString());
    }

    private void parseEnabledHooks(JsonArray hooksArray) {
        for (JsonElement el : hooksArray) {
            String hook = el.getAsString();
            if (hook != null && !hook.isBlank()) {
                enabledHooks.add(hook);
            }
        }
    }

    private void parseStringArray(JsonArray array, List<String> targetList) {
        for (JsonElement el : array) {
            String value = el.getAsString();
            if (value != null && !value.isBlank()) {
                targetList.add(value);
            }
        }
    }

    private void addOptionIfNotEmpty(List<String> list, String flag, List<String> jazzerOpts) {
        if (list != null && !list.isEmpty()) {
            jazzerOpts.add(flag + String.join(":", list));
        }
    }
}