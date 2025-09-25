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

import com.code_intelligence.jazzer.sanitizers.Constants;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * InstrumentationConfig
 *
 * This class is responsible for parsing and holding configuration options for
 * offline instrumentation in Jazzer. The configuration can be specified at compile 
 * time from a JSON file.
 *
 * 
 * Example JSON configuration:
 *
 * {
 *   "instrumentation_filters": {
 *     "include_filter": ["com.example.*"],
 *     "exclude_filter": ["com.example.ignore.*"]
 *   },
 *   "jazzer_hooks": {
 *     "enabled_hooks": [
 *       "com.code_intelligence.jazzer.sanitizers.SqlInjection"
 *     ],
 *     "disabled_hooks": [
 *       "com.code_intelligence.jazzer.sanitizers.SqlInjection"
 *     ]
 *   },
 *   "custom_hooks": {
 *     "custom_hooks_classes": [
 *       "com.code_intelligence.jazzer.hooks.ExampleFuzzerHooks"
 *      ],
 *     "custom_hooks_jar_path": "prebuilts/jazzer/libcustom_hooks.jar"
 *   }
 * }
 *
 * Supplying this JSON configuration file is optional. If the file is not provided, all hooks
 * listed in {@link com.code_intelligence.jazzer.sanitizers.Constants#SANITIZER_HOOK_NAMES}
 * remain disabled by default. If the file is present, Jazzer loads configuration values
 * from it at startup to determine instrumentation and sanitization behavior.
 *
 * The configuration file supports the following options:
 * 
 * ### 1. `instrumentation_filters`
 * This object in the JSON supports two arrays: "include_filter"
 * and "exclude_filter". Both arrays can contain wildcard patterns (for example,
 * "com.example.*"). The include filter specifies which classes are eligible for
 * instrumentation, while the exclude filter specifies which classes should be skipped
 * during instrumentation.
 *
 * ### 2. `jazzer_hooks`
 * This object controls which sanitization hooks are active. These hooks are
 * defined in {@link com.code_intelligence.jazzer.sanitizers.Constants#SANITIZER_HOOK_NAMES}.
 * By default, all such hooks are disabled unless specified otherwise in the configuration.
 * Either "enabled_hooks" or "disabled_hooks" must be specified (but not both). If
 * "disabled_hooks" is used, all hooks will be enabled except those listed. If
 * "enabled_hooks" is used, only the listed hooks will be enabled. Hooks must be written as
 * fully-qualified Java class names.
 *
 * ### 3. `custom_hooks`
 * This object allows users to define additional sanitizer hooks externally.
 * It contains:
 *   - `custom_hooks_classes`: an array of fully-qualified hook class names 
 *      that should be activated during instrumentation and runtime.
 *   - `custom_hooks_jar_path`: a path to the precompiled hooks JAR that contains 
 *      these classes. This can be either an absolute path or a path relative to 
 *      the current working directory. The JAR will be added to the bootstrap 
 *      classpath to ensure proper visibility.
 *
 * Together, these fields enable integrating custom hooks without rebuilding 
 * Jazzer itself. If omitted, no external hooks are loaded.
 */
public class InstrumentationConfig {

    private final Logger logger = Logger.getLogger(InstrumentationConfig.class.getName());

    private final String JAZZER_HOOKS = "jazzer_hooks";
    private final String INSTRUMENTATION_FILTERS = "instrumentation_filters";
    private final String CUSTOM_HOOKS = "custom_hooks";
    private final String CUSTOM_HOOKS_CLASSES = "custom_hooks_classes";
    private final String CUSTOM_HOOKS_JAR_PATH = "custom_hooks_jar_path";
    private final String INCLUDE_FILTER = "include_filter";
    private final String EXCLUDE_FILTER = "exclude_filter";
    private final String DISABLED_HOOKS = "disabled_hooks";
    private final String ENABLED_HOOKS = "enabled_hooks";

    private final Map<String, Boolean> hookStates = new HashMap<>();
    private final List<String> includeFilter = new ArrayList<>();
    private final List<String> excludeFilter = new ArrayList<>();
    private final List<String> customHooksClasses = new ArrayList<>();
    private final Path dumpClassesDir;

    private File customHooksJar;
    
    public InstrumentationConfig() {
        // Disable all the hooks by default
        for (String hook : Constants.SANITIZER_HOOK_NAMES) {
            hookStates.put(hook, false);
        }

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
                    case JAZZER_HOOKS:
                        parseJazzerHooks(json.getAsJsonObject(key));
                        break;
                    case INSTRUMENTATION_FILTERS:
                        parseInstrumentationFilter(json.getAsJsonObject(key));
                        break;
                    case CUSTOM_HOOKS:
                        JsonObject hooksObj = json.getAsJsonObject(key);
                        parseCustomHooksJarPath(hooksObj);
                        parseCustomHooksClasses(hooksObj);
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
        // Collect disabled hooks
        List<String> disabledHooks = hookStates.entrySet().stream()
                .filter(entry -> !entry.getValue())
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        addOptionIfNotEmpty(disabledHooks, "--disabled_hooks=", jazzerOpts);
        addOptionIfNotEmpty(includeFilter, "--instrumentation_includes=", jazzerOpts);
        addOptionIfNotEmpty(excludeFilter, "--instrumentation_excludes=", jazzerOpts);

        if(customHooksJar != null){
            addOptionIfNotEmpty(customHooksClasses, "--custom_hooks=", jazzerOpts);
        }
        jazzerOpts.add("--dump_classes_dir=" + dumpClassesDir.toString());
    }
    
    public File getCustomHooksJar() {
        if(customHooksJar == null){
            logger.warning("custom hooks jar has not provided.");
        }
        return customHooksJar;
    }

    private void parseJazzerHooks(JsonObject hooksObj) {
        JsonArray enabled = hooksObj.has(ENABLED_HOOKS) ? hooksObj.getAsJsonArray(ENABLED_HOOKS) : null;
        JsonArray disabled = hooksObj.has(DISABLED_HOOKS) ? hooksObj.getAsJsonArray(DISABLED_HOOKS) : null;

        if (enabled != null && disabled != null) {
            throw new IllegalArgumentException(
                    "Invalid configuration: Only one of enabled_hooks or disabled_hooks can be specified.");
        }

        if (enabled != null) {
            setHooks(enabled, true /* enabled state */);
        } else if (disabled != null) {
            hookStates.replaceAll((k, v) -> true);
            setHooks(disabled, false /* disabled state */);
        }
    }

    private void setHooks(JsonArray hooks, boolean state) {
        for (JsonElement el : hooks) {
            String hook = el.getAsString();
            if (!hookStates.containsKey(hook)) {
                logger.warning("Unknown hook in enabled_hooks: " + hook);
            }
            hookStates.put(hook, state);
        }
    }

    private void parseInstrumentationFilter(JsonObject filterObj) {
        for (String key : filterObj.keySet()) {
            switch (key) {
                case INCLUDE_FILTER:
                    for (JsonElement el : filterObj.getAsJsonArray(key)) {
                        includeFilter.add(el.getAsString());
                    }
                    break;
                case EXCLUDE_FILTER:
                    for (JsonElement el : filterObj.getAsJsonArray(key)) {
                        excludeFilter.add(el.getAsString());
                    }
                    break;
                default:
                    logger.warning("Unknown key in instrumentation_filter: " + key);
            }
        }
    }

    private void parseCustomHooksClasses(JsonObject hooksObj) {
        if (hooksObj.has(CUSTOM_HOOKS_CLASSES)) {
            JsonArray hooksArray = hooksObj.getAsJsonArray(CUSTOM_HOOKS_CLASSES);
            for (JsonElement el : hooksArray) {
                customHooksClasses.add(el.getAsString());
            }
        } else {
            logger.warning("Expected 'custom_hooks_classes' entry not found in custom_hooks config.");
        }
    }

    private void parseCustomHooksJarPath(JsonObject hooksObj) {
        if (hooksObj.has(CUSTOM_HOOKS_JAR_PATH)) {
            String customHooksJarPath = hooksObj.get(CUSTOM_HOOKS_JAR_PATH).getAsString();
            if (customHooksJarPath != null && !customHooksJarPath.isBlank()) {
                customHooksJar = new File(customHooksJarPath);
                if (!customHooksJar.exists()) {
                    logger.warning("Custom hooks jar not found at: " + customHooksJarPath 
                        + ". Continuing without custom hooks.");
                    customHooksJar = null;
                    return;
                }
            } else {
                logger.warning("No custom hooks jar path provided.");
            }
        } else {
            logger.warning("Expected 'custom_hooks_jar_path' entry not found in custom_hooks config.");
        }
    }

    private void addOptionIfNotEmpty(List<String> list, String flag, List<String> jazzerOpts) {
        if (list != null && !list.isEmpty()) {
            jazzerOpts.add(flag + String.join(":", list));
        }
    }
}