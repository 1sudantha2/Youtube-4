#!/usr/bin/env sh
# One-time bootstrap: generates gradle/wrapper/gradle-wrapper.jar.
# The wrapper JAR is intentionally not committed (binary artifact); run this
# once on any machine that has Gradle 8.x, or just open the project in
# Android Studio and let it sync.
set -eu

VERSION="${1:-8.9}"

if command -v gradle >/dev/null 2>&1; then
    gradle wrapper --gradle-version "$VERSION" --distribution-type bin
else
    echo "Gradle not found. Install Gradle 8.x or open the project in Android Studio." >&2
    exit 1
fi
