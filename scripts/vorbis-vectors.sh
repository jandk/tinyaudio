#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")"

mkdir -p ../test-vectors/vorbis
cd ../test-vectors/vorbis

SUMS=$PWD/vorbis-vectors.sha256
awk '{print $2}' "$SUMS" | wget -q -nc -B "https://people.xiph.org/~xiphmont/test-vectors/vorbis/" -i -
sha256sum -c --quiet "$SUMS"
