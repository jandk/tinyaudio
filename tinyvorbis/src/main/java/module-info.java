module be.twofold.tinyvorbis {
    requires be.twofold.tinyogg;
    requires be.twofold.tinyaudio.core;

    exports be.twofold.tinyvorbis;

    provides be.twofold.tinyogg.OggCodec
        with be.twofold.tinyvorbis.VorbisOggCodec;

    // For testing in IJ
    opens be.twofold.tinyvorbis to org.junit.platform.commons;
}
