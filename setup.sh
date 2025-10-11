#!/bin/bash

# Set up Android SDK and NDK paths
export ANDROID_HOME=/usr/local/google/home/cobark/Android/Sdk
export ANDROID_NDK_HOME=/usr/local/google/home/cobark/Android/Sdk/ndk/29.0.14206865

# Set up AOSP paths
export AOSP_TOP=/usr/local/google/home/cobark/Repos/aosp
export AOSP_R8_PATH=${AOSP_TOP}/prebuilts/r8/r8.jar
export JAZZER_TOP=/usr/local/google/home/cobark/Repos/jazzer
export JAZZER_DEST=${JAZZER_TOP}/third_party/android/r8.jar

# Set up compiler paths
export CC=/usr/bin/gcc
export CXX=/usr/bin/g++

# Ensure PATH includes necessary directories
export PATH=/usr/bin:/bin:/usr/local/bin:$PATH

echo "Environment variables set:"
echo "ANDROID_HOME=$ANDROID_HOME"
echo "ANDROID_NDK_HOME=$ANDROID_NDK_HOME"
echo "CC=$CC"
echo "CXX=$CXX"