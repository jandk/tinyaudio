module be.twofold.tinyaudio.opus {
    requires be.twofold.tinyaudio.core;
    requires be.twofold.tinyaudio.ogg;
    requires java.xml;

    exports be.twofold.tinyaudio.opus;

    provides be.twofold.tinyaudio.ogg.internal.OggCodec
        with be.twofold.tinyaudio.opus.OpusOggCodec;
}
