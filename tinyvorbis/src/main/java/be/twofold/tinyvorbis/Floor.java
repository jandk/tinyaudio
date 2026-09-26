package be.twofold.tinyvorbis;

import java.io.IOException;

public sealed interface Floor permits Floor1 {
    static Floor unpack(BitInputStream bits, int codebookCount) throws IOException {
        int floorType = bits.read(16);
        if (floorType == 0) {
            throw new UnsupportedOperationException("Floor 0");
        } else if (floorType == 1) {
            return Floor1.unpack(bits, codebookCount);
        } else {
            throw new VorbisException("Invalid floor type");
        }
    }

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
