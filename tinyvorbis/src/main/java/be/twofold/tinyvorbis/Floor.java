package be.twofold.tinyvorbis;

import java.io.IOException;
import java.util.List;

sealed interface Floor permits Floor1 {
    static Floor unpack(BitReader bits, int codebookCount) throws IOException {
        int floorType = bits.read(16);
        if (floorType == 0) {
            throw new UnsupportedOperationException("Floor 0");
        } else if (floorType == 1) {
            return Floor1.unpack(bits, codebookCount);
        } else {
            throw new VorbisException("Invalid floor type");
        }
    }

    boolean decode(BitReader bits, List<Codebook> codebooks, int[] floorY);

    void synthesize(int[] floorY, float[] spectrum, int n2);

    default void pack(BitOutputStream bits) throws IOException {
        int floorType;
        if (this instanceof Floor1) {
            floorType = 1;
        } else {
            throw new VorbisException("Invalid floor type");
        }

        bits.write(16, floorType);
        packBody(bits);
    }

    void packBody(BitOutputStream bits) throws IOException;
}
