# Bazel Binary for FuzzApkTool

This directory contains a **prebuilt Bazel binary** required to build the `FuzzApkTool` branch of Jazzer.

---

## Why is this needed?

The `FuzzApkTool` branch depends on a **specific Bazel version** that is not available through regular package managers.  
Other Bazel versions fail to build due to incompatibilities with the `WORKSPACE` configuration and build rules.  

To address this, we provide the **exact Bazel binary** known to work with this PoC.

---

## Usage

To use this binary, specify "tools/bazel" during running any bazel build like:

```bash
tools/bazel build <target>
