import be.twofold.tinyaudio.vorbis.VorbisOggCodec;

module be.twofold.tinyaudio.vorbis {
    requires be.twofold.tinyaudio.ogg;
    requires be.twofold.tinyaudio.core;

    exports be.twofold.tinyaudio.vorbis;

    provides be.twofold.tinyaudio.ogg.OggCodec
        with VorbisOggCodec;

    // For testing in IJ
    opens be.twofold.tinyaudio.vorbis to org.junit.platform.commons;
}
