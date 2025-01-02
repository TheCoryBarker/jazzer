#!/usr/bin/env bash
# Copyright 2023 Code Intelligence GmbH
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.


set -euo pipefail

THIS_DIR="$(pwd -P)"

# Licence headers
bazelisk run --config=quiet //:addlicense -- -c "Code Intelligence GmbH" -ignore '**/third_party/**' -ignore '**/.github/**' "$THIS_DIR"

# C++ & Java
find "$THIS_DIR" \( -name '*.cpp' -o -name '*.c' -o -name '*.h' -o -name '*.java' \) -print0 | xargs -0 bazelisk run --config=quiet //:clang-format -- -i
