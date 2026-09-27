module be.twofold.tinyaudio.vorbis {
    requires be.twofold.tinyaudio.ogg;
    requires be.twofold.tinyaudio.core;

    exports be.twofold.tinyvorbis;

    provides be.twofold.tinyaudio.ogg.OggCodec
        with be.twofold.tinyvorbis.VorbisOggCodec;

    // For testing in IJ
    opens be.twofold.tinyvorbis to org.junit.platform.commons;
}
