#!/usr/bin/env bash
# Fetches and vendors the Genesis Plus GX libretro core into
# app/src/main/cpp/core-src/ at the given commit (default: the one
# this project was built and verified against).
set -euo pipefail
COMMIT="${1:-c2838c7dc4236fc2fe94e5dbd08b41486067918e}"
DEST="$(dirname "$0")/../app/src/main/cpp/core-src"
if [ -d "$DEST/core" ]; then echo "core already vendored at $DEST"; exit 0; fi
mkdir -p "$DEST"
git clone --depth 1 https://github.com/libretro/Genesis-Plus-GX.git "$DEST"
cd "$DEST" && git fetch --depth 1 origin "$COMMIT" && git checkout "$COMMIT"
git submodule update --init --depth 1
echo "done: core vendored at $DEST"
