#!/usr/bin/env bash
set -euo pipefail

if [[ $# -lt 1 ]]; then
    echo "Usage: ./build.sh /path/to/vanted-core.jar [output-directory]" >&2
    exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
VANTED_CORE_JAR="$1"
OUTPUT_DIR="${2:-$SCRIPT_DIR/out}"
SOURCE_ROOT="$SCRIPT_DIR/src"
CLASSES_DIR="$OUTPUT_DIR/classes"
JAR_PATH="$OUTPUT_DIR/OgdfIntegration.jar"

if [[ ! -f "$VANTED_CORE_JAR" ]]; then
    echo "VANTED core JAR not found: $VANTED_CORE_JAR" >&2
    exit 1
fi

command -v javac >/dev/null 2>&1 || {
    echo "javac was not found. Install a full JDK and add its bin directory to PATH." >&2
    exit 1
}
command -v jar >/dev/null 2>&1 || {
    echo "jar was not found. Install a full JDK and add its bin directory to PATH." >&2
    exit 1
}

mkdir -p "$CLASSES_DIR"
SOURCES=()
while IFS= read -r source; do
    SOURCES+=("$source")
done < <(find "$SOURCE_ROOT" -name '*.java' -print)

if [[ ${#SOURCES[@]} -eq 0 ]]; then
    echo "No Java source files were found below $SOURCE_ROOT." >&2
    exit 1
fi

javac -encoding UTF-8 -cp "$VANTED_CORE_JAR" -d "$CLASSES_DIR" "${SOURCES[@]}"
cp "$SOURCE_ROOT/OgdfIntegration.xml" "$CLASSES_DIR/OgdfIntegration.xml"
jar --create --file "$JAR_PATH" -C "$CLASSES_DIR" .

echo "Created $JAR_PATH"
