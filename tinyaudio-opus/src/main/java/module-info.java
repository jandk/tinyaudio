module be.twofold.tinyaudio.opus {
    requires be.twofold.tinyaudio.core;
    requires be.twofold.tinyaudio.ogg;

    exports be.twofold.tinyaudio.opus;

    provides be.twofold.tinyaudio.ogg.OggCodec
        with be.twofold.tinyaudio.opus.OpusOggCodec;
}
