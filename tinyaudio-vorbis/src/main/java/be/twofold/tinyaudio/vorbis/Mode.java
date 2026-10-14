package be.twofold.tinyaudio.vorbis;

import java.io.IOException;

final class Mode {
    private final boolean blockFlag;
    private final int mapping;

    Mode(
        boolean blockFlag,
        int mapping
    ) {
        this.blockFlag = blockFlag;
        this.mapping = mapping;
    }

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

    boolean blockFlag() {
        return blockFlag;
    }

    int mapping() {
        return mapping;
    }
}
