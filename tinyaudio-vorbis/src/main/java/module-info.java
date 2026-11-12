module be.twofold.tinyaudio.vorbis {
    requires be.twofold.tinyaudio.core;
    requires be.twofold.tinyaudio.ogg;

    exports be.twofold.tinyaudio.vorbis;

    provides be.twofold.tinyaudio.ogg.OggCodec
        with be.twofold.tinyaudio.vorbis.VorbisOggCodec;
}
