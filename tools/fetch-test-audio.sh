#!/usr/bin/env bash
#
# Downloads the real instrument recordings used by RealRecordingPitchTest and
# converts them to the mono 44.1 kHz WAV the test expects.
#
# The files are a few tens of megabytes, so they are not kept in version
# control; without them those tests skip and the rest of the suite still runs.
#
# Source: University of Iowa Electronic Music Studios, Musical Instrument
# Samples — https://theremin.music.uiowa.edu/MIS.html
# Free to use; please credit the Iowa studios if you redistribute them.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEST="$ROOT/core/src/jvmTest/resources/realaudio"
BASE="https://theremin.music.uiowa.edu/sound%20files/MIS"

if ! command -v ffmpeg >/dev/null; then
    echo "ffmpeg is required to convert the recordings." >&2
    exit 1
fi

mkdir -p "$DEST"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

fetch() {
    local url="$1" out="$2"
    if [ -f "$DEST/$out" ]; then
        echo "✓ $out (already present)"
        return
    fi
    echo "↓ $out"
    curl -fsS -o "$tmp/raw.aif" "$url"
    ffmpeg -y -v error -i "$tmp/raw.aif" -ac 1 -ar 44100 -c:a pcm_s16le "$DEST/$out"
}

# Chromatic runs on a single string, so the expected pitches are known exactly.
fetch "$BASE/Piano_Other/guitar/Guitar.mf.sulE.E2B2.stereo.aif"        guitar_E2-B2.wav
fetch "$BASE/Strings/cello2012/Cello.arco.mf.sulC.C2B2.stereo.aif"     cello_C2-B2.wav
fetch "$BASE/Strings/violin2012/Violin.arco.mf.sulG.G3B3.stereo.aif"   violin_G3-B3.wav

echo
echo "Done. Run: ./gradlew :app:testDebugUnitTest --tests '*RealRecording*'"
