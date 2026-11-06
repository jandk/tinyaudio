module be.twofold.tinyaudio.vorbis {
    requires be.twofold.tinyaudio.ogg;
    requires be.twofold.tinyaudio.core;

    exports be.twofold.tinyaudio.vorbis;

    provides be.twofold.tinyaudio.ogg.OggCodec
        with be.twofold.tinyaudio.vorbis.VorbisOggCodec;
}
