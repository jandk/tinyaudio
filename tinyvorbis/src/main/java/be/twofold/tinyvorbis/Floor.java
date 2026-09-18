package be.twofold.tinyvorbis;

import java.io.IOException;

public sealed interface Floor permits Floor1 {
    static Floor read(BitInputStream bits) throws IOException {
        int floorType = bits.read(16);
        if (floorType == 0) {
            throw new UnsupportedOperationException("Floor 0");
        } else if (floorType == 1) {
            return Floor1.read(bits);
        } else {
            throw new VorbisException("Invalid floor type");
        }
    }

    default void write(BitOutputStream bits) throws IOException {
        int floorType;
        if (this instanceof Floor1) {
            floorType = 1;
        } else {
            throw new VorbisException("Invalid floor type");
        }

        bits.write(16, floorType);
        write0(bits);
    }

    void write0(BitOutputStream bits) throws IOException;
}
