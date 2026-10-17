#!/usr/bin/env bash
# Builds the signed release APKs, and publishes them as a GitHub release.
#
#   scripts/release.sh apk       build/release/relaix.apk and relaix-arm64.apk
#   scripts/release.sh publish   the same, then GitHub release v<versionName>
#
# Run where the release key is (keystore.properties, release.jks: gitignored,
# back them up). Raise versionCode and versionName in app/build.gradle.kts
# first: Android installs an APK over another only with a higher versionCode.
set -euo pipefail
cd "$(dirname "$0")/.."

version=$(grep -m1 'versionName = ' app/build.gradle.kts | sed -E 's/.*"(.*)".*/\1/')

apk() {
  ./gradlew :app:assembleRelease :app:testDebugUnitTest --console=plain -q
  mkdir -p build/release
  cp app/build/outputs/apk/release/app-universal-release.apk build/release/relaix.apk
  cp app/build/outputs/apk/release/app-arm64-v8a-release.apk build/release/relaix-arm64.apk
  ls -la build/release
}

publish() {
  git diff --quiet HEAD || { echo "commit and push first: the release is tagged on what is pushed" >&2; exit 1; }
  apk
  gh release create "v$version" build/release/relaix.apk build/release/relaix-arm64.apk \
    --repo KGMA74/relaix-agent --target "$(git rev-parse HEAD)" --title "Relaix $version" \
    --notes "relaix.apk : tous les téléphones. relaix-arm64.apk : plus léger, pour les téléphones récents (arm64). S'installe par-dessus la version précédente : enrôlement et coffre du PIN conservés."
}

case "${1:-}" in
  apk) apk ;;
  publish) publish ;;
  *) echo "usage: $0 apk|publish" >&2; exit 2 ;;
esac
