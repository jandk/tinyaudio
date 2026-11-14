#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")"

SUMS=$PWD/vorbis-vectors.sha256
mkdir -p ../test-vectors/vorbis
cd ../test-vectors/vorbis

awk '{print $2}' "$SUMS" | wget -q -nc -B "https://people.xiph.org/~xiphmont/test-vectors/vorbis/" -i -
sha256sum -c --quiet "$SUMS"
