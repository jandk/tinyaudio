package be.twofold.tinyvorbis;

import java.io.IOException;

record Mode(
    boolean blockFlag,
    int mapping
) {
    static Mode unpack(BitReader bits, int mappingCount) throws IOException {
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
        if (mapping >= mappingCount) {
            throw new VorbisException("Mode mapping out of range: " + mapping);
        }

        return new Mode(blockFlag, mapping);
    }

    void pack(BitOutputStream bits) throws IOException {
        bits.writeBool(blockFlag);
        bits.write(16, 0);
        bits.write(16, 0);
        bits.write(8, mapping);
    }
}
