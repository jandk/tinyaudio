#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")"

SUMS=$PWD/opus-vectors.sha256
mkdir -p ../test-vectors/opus
cd ../test-vectors/opus

wget -q -nc "https://opus-codec.org/static/testvectors/opus_testvectors-rfc8251.tar.gz"
sha256sum -c --quiet "$SUMS"
tar -xzf opus_testvectors-rfc8251.tar.gz --strip-components=1
