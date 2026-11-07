package be.twofold.tinyaudio.vorbis;

import be.twofold.tinyaudio.core.internal.BitReader;

import java.io.IOException;
import java.util.List;

interface Floor {
    static Floor unpack(BitReader bits, int codebookCount) throws IOException {
        int floorType = bits.read(16);
        if (floorType == 0) {
            throw new VorbisException("Floor 0 is not supported");
        } else if (floorType == 1) {
            return Floor1.unpack(bits, codebookCount);
        } else {
            throw new VorbisException("Invalid floor type");
        }
    }

    boolean decode(BitReader bits, List<Codebook> codebooks, int[] floorY);

    void synthesize(int[] floorY, float[] spectrum, int n2);
}
