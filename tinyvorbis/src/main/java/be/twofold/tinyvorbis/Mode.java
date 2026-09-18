package be.twofold.tinyvorbis;

import java.io.IOException;

record Mode(
    boolean blockFlag,
    int mapping
) {
    static Mode read(BitInputStream bits) throws IOException {
        boolean blockFlag = bits.read(1) == 1;
        int windowType = bits.read(16);
        if (windowType != 0) {
            throw new VorbisException("Invalid window type: " + windowType);
        }
        int transformType = bits.read(16);
        if (transformType != 0) {
            throw new VorbisException("Invalid transform type: " + transformType);
        }
        int mapping = bits.read(8);

        return new Mode(blockFlag, mapping);
    }

    void write(BitOutputStream bits) throws IOException {
        bits.writeBool(blockFlag);
        bits.write(16, 0);
        bits.write(16, 0);
        bits.write(8, mapping);
    }
}
