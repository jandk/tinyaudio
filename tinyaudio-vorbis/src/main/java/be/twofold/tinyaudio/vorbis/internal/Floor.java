package be.twofold.tinyaudio.vorbis.internal;

import be.twofold.tinyaudio.core.AudioFormatException;
import be.twofold.tinyaudio.core.internal.BitReader;

import java.io.IOException;
import java.util.List;

interface Floor {
    static Floor unpack(BitReader bits, List<Codebook> codebooks) throws IOException {
        int floorType = bits.read(16);
        if (floorType == 0) {
            return Floor0.unpack(bits, codebooks);
        } else if (floorType == 1) {
            return Floor1.unpack(bits, codebooks.size());
        } else {
            throw new AudioFormatException("Invalid floor type");
        }
    }

    boolean decode(BitReader bits, List<Codebook> codebooks, float[] floorY);

    void synthesize(float[] floorY, float[] spectrum, int n2);
}
