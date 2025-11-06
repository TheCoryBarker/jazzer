"""Android Java toolchain configuration."""

def register_android_java_toolchains():
    """Register Java toolchains compatible with Android platforms."""

    # Register a Java runtime toolchain that's compatible with Android platforms
    # This uses the host JDK for compilation but marks it as compatible with Android
    native.toolchain(
        name = "android_java_runtime_toolchain",
        toolchain = "@local_jdk//:jdk",
        toolchain_type = "@bazel_tools//tools/jdk:runtime_toolchain_type",
        target_compatible_with = [
            "@platforms//os:android",
        ],
    )