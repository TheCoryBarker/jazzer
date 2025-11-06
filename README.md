# Jazzer AOSP Integration

## Overview

This project integrates [Jazzer](https://github.com/CodeIntelligenceTesting/jazzer) fuzzing engine into the Android Open Source Project (AOSP) project to enable fuzzing of Java programs, apps, and system services.

The core of this integration is a custom wrapper around the R8 compiler, which allows Jazzer to instrument application code just before it is converted into DEX format, mostly because this is the easiest way to insert ourselves into the compilation process consistently without checking in a new Soong build rule. It will also allow this to be portable to other projects, all they need to do is replace their version of r8 with ours (and maybe some other tweaks depending on the project).

Currently this is a working prototype, but to check this in to AOSP or make it ready for seamless integration with other build systems or upstream to Jazzer, we would like to see some ROI first.

## Features
*   **Seamless AOSP Integration:** By using the `aosp_setup.sh` script and referencing the example IntentRedirection sanitizer and example app, you can start fuzzing in minutes.
*   **Compile-Time Instrumentation:** Instruments Java/Kotlin code during the build process, eliminating the need for runtime agents.
*   **Coverage-Guided Fuzzing:** Leverages libFuzzer's feedback-driven engine to intelligently explore code paths.
*   **Customizable Sanitizers:** Add your own sanitizers to the sanitizer library created in AOSP when you run the setup script.

## How It Works

Because there is not reliable way to instrument DEX bytecode the same as JVM bytecode, we need to instrument the JVM bytecode before it is converted to DEX. There are tools like Dexter/Slicer that can do some limited bytecode manipulation on DEX bytecode, but it is very limited, therefore, we instrument the JVM bytecode just before the DEXing process during compilation, and we've removed the runtime instrumentation. We have also removed the sanitizers from the Jazzer project, and made it so the sanitizer are added to a sanitizers library in AOSP that is created when the setup script is ran. This creates a single way to add sanitizers to your fuzzer, simplifying the process, and removing the need for a developer to revisit the Jazzer repository after they've ran the setup script.

The helper script should take care of most of the setup, but the high level steps that are happening under the covers as you run the setup script are:

* Build the compile time instrumention tool by copying over the r8.jar from the prebuilts directory in AOSP, and then building our own r8.jar that will contain the r8.jar from AOSP as well as our own wrapper code that will instrument the bytecode.
* Copy over our r8.jar to the r8 prebuilts directory in AOSP, replacing the original r8.jar, and update the main class entry point in the r8/Android.bp to point to our wrapper.
* Build the Jazzer runtime library and the JNI shared libraries that will be needed by the runtime library, and copy those over into AOSP, and create an Android.bp build rule to create an Android library that can be depended on by our fuzz target.
* Copy over the Android sanitizers to AOSP and create a build rule for the sanitizers so we can easily add new sanitizers without needing to build anything in the Jazzer repo and copying it over.
* Create your fuzz target, reference the newly created `app_fuzz_lib` build target which will contain all of the runtime dependencies Jazzer will need, and build it. Since we have replaced the r8.jar, the output will be instrumented.
* Install/push to you phone and run.


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

## Running
Use the setup script to build and set up AOSP, 

### `configure`
The setup script only needs three things: the path to AOSP root, your Android SDK, and the NDK. You can run the configure command with these parameters and a `configuration.sh` file will be created to save these for all future commands.

**Command:**
```shell
source aosp_setup.sh configure --aosp_home <path_to_aosp> --sdk_home <path_to_sdk> --ndk_home <path_to_ndk>
```

### `setup_r8`
```
$ ./aosp_setup.sh setup_r8
```
This command will use the values from the `configure` step to copy over the r8.jar from AOSP (if it hasn't been copied over already) into the Jazzer repo to be built into our custom r8.jar. Then this step will build the new r8.jar, and copy it over to AOSP.

There is one manual step here, you will need to go over to $AOSP_ROOT/prebuilts/r8/Android.bp and change this entry:

```
java_binary_host {
    name: "r8",
    main_class: "com.android.tools.r8wrappers.R8Wrapper",
    static_libs: ["r8lib"]
}
```

to this:

```
java_binary_host {
    name: "r8",
    main_class: "com.code_intelligence.jazzer.r8.R8Wrapper",
    static_libs: ["r8lib"],
}
```

### `setup_jazzer_runtime`
```
$ ./aosp_setup.sh setup_jazzer_runtime
```

This command will do a few things:
1: Build the needed Jazzer runtime JAR, the native libraries, copy them to AOSP, and then create an Android.bp in AOSP for these.
2: Copy over the IntentRedirection sanitizer into AOSP, and create an Android.bp entry for the sanitizers library.
3: Copy over the example App fuzzer to use as a reference when creating your own fuzzer, found at `tools/security/fuzzing/jazzer/jazzer_example` in AOSP.

### `setup`
```
$ ./aosp_setup.sh setup
```

This command will run both setup_r8 and setup_jazzer_runtime together.

### Running the Example

After running through the setup script above, you can then focus on AOSP. To build and run the Example fuzzer app, follow these steps.

1: Build it by using these commands
```
$ cd $AOSP_ROOT
$ source build/envsetup.sh
$ lunch aosp_arm64-trunk_staging-userdebug
$ mma AppExample -j88
```

2: Install it onto your device using
```
$ adb install $ANDROID_PRODUCT_OUT/system/app/AppExample/AppExample.apk
```

3: Start fuzzing. This should just be an activity instead of a background service, but since it is a background service, you may need to open your app to start fuzzing if you get an error message. After you have opened your app, you can run the command to start fuzzing:
```
$ am startservice -a com.code_intelligence.jazzer.driver.AndroidStartFuzz -e target_class <fully_qualified_classpath>
```

so for the example app, this command would be:
```
$ am startservice -a com.code_intelligence.jazzer.driver.AndroidStartFuzz -e target_class com.app.example.ExampleFuzzer
```

## This will also work for JARs if you instead of creating an android_app you create a java_binary. With this configuration anything you build will end up instrumented, you will just need to reference the jazzer runtime as a dependency, and use the example app for reference.
