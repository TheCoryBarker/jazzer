
java_import (
  name = "r8",
  jars = ["r8.jar"],
)

java_library(
  name = "local_r8",
  runtime_deps = [
    ":r8",                 # for testing using local r8
    #"@r8_remote//jar",    # for testing using remote r8
  ],
  visibility = ["//visibility:public"],
)