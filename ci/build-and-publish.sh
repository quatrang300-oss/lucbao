#!/usr/bin/env bash
# Builds the engine (and optionally the app) and publishes them as GitHub releases:
#   engine-latest : engine.apk + engine.json  (downloaded silently by every installed app)
#   app-latest    : LucBao.apk + app.json     (app self-update + the link you share)
# Usage: build-and-publish.sh engine|all
set -euo pipefail
MODE="${1:-all}"

if [ -z "${KEYSTORE_BASE64:-}" ] || [ -z "${SIGNING_PASSWORD:-}" ]; then
  echo "::error::Missing repository secrets KEYSTORE_BASE64 / KEYSTORE_PASSWORD (see README)."
  exit 1
fi
echo "$KEYSTORE_BASE64" | base64 -d > "$RUNNER_TEMP/lucbao.jks"
export SIGNING_KEYSTORE="$RUNNER_TEMP/lucbao.jks"
export SIGNING_ALIAS="${SIGNING_ALIAS:-lucbao}"
export UPDATE_REPO="$GITHUB_REPOSITORY"
export ENGINE_VERSION=$(( $(date +%s) / 60 ))
export APP_VERSION_CODE=$(( $(date +%s) / 60 ))
export APP_VERSION_NAME="1.$(date -u +%Y%m%d).$(date -u +%H%M)"

# JitPack builds NewPipeExtractor on first request, which can take minutes: warm it up.
EXTRACTOR_REF="$(tr -d '[:space:]' < engine/extractor.version)"
curl -fsS -o /dev/null --retry 20 --retry-delay 30 --retry-all-errors --max-time 120 \
  "https://jitpack.io/com/github/TeamNewPipe/NewPipeExtractor/$EXTRACTOR_REF/NewPipeExtractor-$EXTRACTOR_REF.pom" \
  || echo "JitPack warm-up did not answer, trying the build anyway"

chmod +x gradlew
if [ "$MODE" = "engine" ]; then
  ./gradlew --no-daemon --stacktrace :engine:assembleRelease
else
  ./gradlew --no-daemon --stacktrace :engine:assembleRelease :app:assembleRelease
fi

mkdir -p out
cp engine/build/outputs/apk/release/engine-release.apk out/engine.apk
EXTRACTOR="$(tr -d '[:space:]' < engine/extractor.version)"
cat > out/engine.json <<JSON
{"version":$ENGINE_VERSION,"api":1,"extractor":"$EXTRACTOR","file":"engine.apk","sha256":"$(sha256sum out/engine.apk | cut -d' ' -f1)"}
JSON

ensure_release() {
  gh release view "$1" >/dev/null 2>&1 || gh release create "$1" --title "$2" --notes "$3" --latest="$4"
}

ensure_release engine-latest "Bộ phát YouTube (tự cập nhật)" \
  "Tệp này do Lục Bảo tự tải về và dùng. Bạn không cần tải thủ công." false
# Upload the file first, then the json that points to it.
gh release upload engine-latest out/engine.apk --clobber
gh release upload engine-latest out/engine.json --clobber

if [ "$MODE" != "engine" ]; then
  cp app/build/outputs/apk/release/app-release.apk out/LucBao.apk
  cat > out/app.json <<JSON
{"versionCode":$APP_VERSION_CODE,"versionName":"$APP_VERSION_NAME","file":"LucBao.apk","sha256":"$(sha256sum out/LucBao.apk | cut -d' ' -f1)"}
JSON
  ensure_release app-latest "Lục Bảo – bản mới nhất" \
    "Tải **LucBao.apk** bên dưới để cài. Máy đã cài sẽ tự cập nhật, không cần tải lại." true
  gh release upload app-latest out/LucBao.apk --clobber
  gh release upload app-latest out/app.json --clobber
  gh release edit app-latest --title "Lục Bảo $APP_VERSION_NAME" >/dev/null
fi

echo "Published engine $ENGINE_VERSION ($EXTRACTOR) mode=$MODE"
{
  echo "### Lục Bảo"
  echo "- Bộ phát YouTube: #$ENGINE_VERSION · NewPipeExtractor $EXTRACTOR"
  if [ "$MODE" != "engine" ]; then
    echo "- Ứng dụng: $APP_VERSION_NAME ($APP_VERSION_CODE)"
    echo "- Link tải để chia sẻ: https://github.com/$GITHUB_REPOSITORY/releases/download/app-latest/LucBao.apk"
  fi
} >> "$GITHUB_STEP_SUMMARY"
