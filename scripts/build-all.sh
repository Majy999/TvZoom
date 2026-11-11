#!/bin/sh
set -eu

"$(dirname "$0")/build-dex.sh"
"$(dirname "$0")/build.sh"
