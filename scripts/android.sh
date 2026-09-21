#!/usr/bin/env bash
set -euo pipefail
component=$(cd "$(dirname "$0")/.." && pwd)
export ANDROID_HOME=${ANDROID_HOME:-$HOME/Android/Sdk}
if [[ -z ${JAVA_HOME:-} && -x /usr/lib/jvm/java-21-openjdk/bin/java ]]; then
    export JAVA_HOME=/usr/lib/jvm/java-21-openjdk
fi
cd "$component"
exec ./gradlew "$@"
