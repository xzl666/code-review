#!/usr/bin/env sh
set -eu

TARGET_OS="${1:-$(go env GOOS)}"
TARGET_ARCH="${2:-$(go env GOARCH)}"
PROJECT_ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
SOURCE_ROOT="$PROJECT_ROOT/vendor/open-code-review"
RESOURCE_ROOT="$PROJECT_ROOT/src/main/resources/ocr/$TARGET_OS-$TARGET_ARCH"
EXTENSION=""
[ "$TARGET_OS" = "windows" ] && EXTENSION=".exe"
BINARY="$RESOURCE_ROOT/opencodereview$EXTENSION"

command -v go >/dev/null 2>&1 || { echo "Go 1.25.5 or newer is required" >&2; exit 1; }
[ -f "$SOURCE_ROOT/go.mod" ] || { echo "vendor/open-code-review source is missing" >&2; exit 1; }

mkdir -p "$RESOURCE_ROOT"
COMMIT=$(cut -c1-7 "$SOURCE_ROOT/UPSTREAM_COMMIT")
BUILD_DATE=$(date -u +"%Y-%m-%dT%H:%M:%SZ")
(
  cd "$SOURCE_ROOT"
  CGO_ENABLED=0 GOOS="$TARGET_OS" GOARCH="$TARGET_ARCH" go build -trimpath \
    -ldflags "-s -w -X main.Version=v1.7.9 -X main.GitCommit=$COMMIT -X main.BuildDate=$BUILD_DATE" \
    -o "$BINARY" ./cmd/opencodereview
)

if command -v sha256sum >/dev/null 2>&1; then
  sha256sum "$BINARY" | awk '{print $1}' > "$BINARY.sha256"
else
  shasum -a 256 "$BINARY" | awk '{print $1}' > "$BINARY.sha256"
fi
chmod +x "$BINARY"
gzip -n -f "$BINARY"
echo "OCR generated: $BINARY.gz"
