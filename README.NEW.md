# Jazzer AOSP Integration

## Overview

This project provides a comprehensive solution for integrating the [Jazzer](https://github.com/CodeIntelligenceTesting/jazzer) fuzzing engine into the Android Open Source Project (AOSP) build system. It enables powerful, coverage-guided, in-process fuzzing of Android applications and services by applying instrumentation at compile time.

The core of this integration is a custom wrapper around the R8 compiler, which allows Jazzer to instrument application code just before it is converted into DEX format. This ensures that fuzzing can be performed on the final, production-ready code, providing the most accurate results.

## Features

*   **Seamless AOSP Integration:** Uses standard AOSP build mechanisms (`Android.bp`) for easy adoption.
*   **Compile-Time Instrumentation:** Instruments Java/Kotlin code during the build process, eliminating the need for runtime agents.
*   **Coverage-Guided Fuzzing:** Leverages libFuzzer's feedback-driven engine to intelligently explore code paths.
*   **Customizable Sanitizers:** Includes a powerful framework for enabling and disabling specific security checks (e.g., SQL Injection, Command Injection, Deserialization).
*   **Runtime Fuzzing:** Provides a simple mechanism to start and stop fuzzing on a device or emulator via an Android component.

## How It Works

The project operates in two main phases: compile-time instrumentation and runtime fuzzing.

### Phase 1: Compile-Time Instrumentation

This phase hooks into the AOSP build process to inject Jazzer's instrumentation into the target application's code.

1.  **The Custom R8 Wrapper (`r8_deploy.jar`):**
    The `aosp_setup.sh` script configures the build to produce a custom `r8_deploy.jar`. This JAR is not a reimplementation of R8; instead, it's a wrapper that contains both the real AOSP R8 compiler and the Jazzer instrumentor. This wrapper is designed to replace the standard `r8.jar` used in your AOSP build.

2.  **The Build Hook:**
    The wrapper intercepts the build process at a critical moment: after Java/Kotlin code has been compiled to JVM bytecode but *before* R8 performs its main functions (like desugaring, shrinking, and converting to DEX format). This is the ideal point to apply instrumentation, as it operates on the complete bytecode while preserving the code structure.

3.  **The Instrumentation Process:**
    Inside the wrapper, before invoking the real R8 compiler, the Jazzer instrumentor is executed. It analyzes the application's bytecode and injects probes for:
    *   **Coverage Tracking:** To guide the fuzzing engine.
    *   **Data Flow Analysis:** To trace tainted data through the application.
    *   **Sanitizers:** To detect potential security vulnerabilities.

4.  **Sanitizer Configuration:**
    You can control which sanitizers are active by providing a JSON configuration file. The `aosp_setup.sh` script will create a sanitizer library project in AOSP, and this configuration tells the instrumentor which sanitizers from that library to apply. The configuration file should be placed in your fuzzing target's source directory within AOSP and referenced in its `Android.bp` file.

    **Example `instrumentation_config.json`:**
    ```json
    {
      "enabled_hooks": [
        "com.code_intelligence.jazzer.sanitizers.IntentRedirection",
        "com.code_intelligence.jazzer.sanitizers.Deserialization",
        "com.code_intelligence.jazzer.sanitizers.OsCommandInjection",
        "com.code_intelligence.jazzer.sanitizers.SqlInjection"
      ],
      "instrumentation_includes": [
        "com.myapp.package.**"
      ],
      "instrumentation_excludes": [
        "com.myapp.package.test.**",
        "com.google.common.**"
      ]
    }
    ```

### Phase 2: Runtime Fuzzing

After the instrumented APK is built and installed, this phase manages the execution of the fuzzer.

1.  **The Fuzzing Library (`jazzer.jar`):**
    The setup script also creates a new Java library in AOSP that includes the `jazzer.jar` and all necessary JNI libraries (`.so` files). This library contains the fuzzing engine, the instrumented code's runtime components, and the Android-specific entry point for starting the fuzzer.

2.  **AOSP Integration:**
    This library is added as a dependency to your target application in AOSP. The build system merges the included `AndroidManifest.xml`, which defines an exported service that acts as the entry point for the fuzzer.

3.  **Starting a Fuzzing Run:**
    With the instrumented APK installed on a device or emulator, you can start the fuzzing process using the Android Activity Manager (`am`). The fuzzer will attach to the running application process and begin feeding generated inputs to the function you defined in your fuzz target.

    To start fuzzing, execute the following command:
    ```shell
    am startservice -a com.code_intelligence.jazzer.driver.AndroidStartFuzz
    ```

## Setup Script (`aosp_setup.sh`)

The `aosp_setup.sh` script is the primary tool for configuring and deploying the Jazzer integration into your AOSP checkout. It automates the process of setting up paths, building the necessary components, and placing them in the correct locations.

The setup process is divided into three main commands, which should be run in the following order:

### 1. `configure`

This command initializes the setup by creating a `configuration.sh` file. This file stores the absolute paths to your AOSP, Android SDK, and Android NDK directories, which are required by the subsequent build and setup commands. It also performs an initial copy of the R8 compiler from your AOSP checkout to the local project.

**Command:**
```shell
source aosp_setup.sh configure --aosp_home <path_to_aosp> --sdk_home <path_to_sdk> --ndk_home <path_to_ndk>
```

**Verification:**
Upon successful execution, you should see output similar to the following, confirming that the configuration has been written and the initial R8 JAR has been copied:
```
Wrote configuration to /path/to/jazzer/configuration.sh
Copied initial local r8.jar from AOSP to /path/to/jazzer/third_party/android/r8.jar
Configuration complete.
```

### 2. `setup_r8`

This command builds the custom R8 wrapper (`r8_deploy.jar`) and replaces the standard `r8.jar` in your AOSP `prebuilts` directory with this new version. It also clears any cached versions of R8 from your AOSP `out` directory to ensure the build system picks up the change.

**Command:**
```shell
source aosp_setup.sh setup_r8
```

**Verification:**
A successful run will show the Bazel build output, followed by a confirmation that the `r8.jar` in AOSP has been updated:
```
<Bazel build output>
INFO: Build completed successfully, <N> total actions

Updated AOSP r8.jar at /path/to/aosp/prebuilts/r8/r8.jar
```

### 3. `setup_jazzer_runtime`

This command builds the Jazzer runtime components, including the `jazzer.jar` and the necessary JNI shared libraries (`.so` files). It then copies these files, along with the AOSP build files (`Android.bp`, `AndroidManifest.xml`, etc.), into a new `tools/security/fuzzing/jazzer` directory within your AOSP checkout. This makes the Jazzer runtime available as a library that can be included in your fuzzing targets.

**Command:**
```shell
source aosp_setup.sh setup_jazzer_runtime
```

**Verification:**
The output will show another Bazel build, followed by a series of messages confirming that the JAR and shared libraries have been extracted and copied to the AOSP directory:
```
<Bazel build output>
INFO: Build completed successfully, <N> total actions

JAZZER_JAR_PATH=/tmp/jazzer_extract.XXXX/lib/to/jazzer.jar
JAZZER_SO_COUNT=4
JAZZER_SO_PATHS=/path/to/libone.so /path/to/libtwo.so ...
Copied libjazzer_driver.so to /path/to/aosp/tools/security/fuzzing/jazzer/lib/arm64
... (more .so files) ...
Copied jazzer.jar to /path/to/aosp/tools/security/fuzzing/jazzer
Copied contents of /path/to/jazzer/aosp_files to /path/to/aosp/tools/security/fuzzing/jazzer (structure preserved).
```

After running these three commands in order, your AOSP environment will be fully configured to use Jazzer for compile-time instrumentation and runtime fuzzing.

## Getting Started

1.  **Configuration:** Run the `aosp_setup.sh` script to generate your configuration file and set up the necessary build targets in your AOSP checkout.
2.  **Build:** Execute the Bazel build command to create the `r8_deploy.jar` and the AOSP fuzzing library.
    ```shell
    source ./setup.sh && bazelisk build --config=android_arm \
        //src/main/java/com/code_intelligence/jazzer/android:r8_deploy.jar \
        //src/main/native/com/code_intelligence/jazzer/driver:jazzer_driver \
        //src/main/native/com/code_intelligence/jazzer/driver:jazzer_fuzzed_data_provider \
        //src/main/native/com/code_intelligence/jazzer/driver:jazzer_signal_handler \
        //src/main/native/com/code_intelligence/jazzer:jazzer_preload
    ```
3.  **Integration:**
    *   Replace the existing `r8.jar` in your AOSP `prebuilts` directory with the newly generated `r8_deploy.jar`.
    *   Add the generated Jazzer library as a dependency to your application's `Android.bp` file.
4.  **Build & Fuzz:** Build your AOSP target, install the resulting APK, and start fuzzing using the `am` command.

## Analysis: Applicability to Standard JARs

The current implementation is **tightly coupled with the Android build system** and is not designed to work with standard, non-Android JARs out-of-the-box.

The primary reasons for this are:
*   **R8 Dependency:** The entire instrumentation process is built around wrapping R8, which is the code optimizer and dexer for the Android toolchain. Standard Java projects typically use build systems like Maven or Gradle and do not involve R8.
*   **Android Components:** The runtime fuzzing mechanism relies on Android-specific components, namely an exported `Service` defined in `AndroidManifest.xml` and started via the `am` command. This framework does not exist in a standard Java environment.
*   **Build System Integration:** The setup scripts and Bazel build rules are specifically designed to integrate with AOSP's build logic (`Android.bp`).

To make this process work for a normal JAR, one would need to re-architect the solution significantly, likely by creating a new instrumentation wrapper that hooks into a different part of a standard Java build process (e.g., a Maven or Gradle plugin) and developing a new runtime entry point suitable for a standard JVM.
