module be.twofold.tinyaudio.ogg {
    requires be.twofold.tinyaudio.core;

    exports be.twofold.tinyaudio.ogg;

    provides be.twofold.tinyaudio.core.AudioDecoderProvider
        with be.twofold.tinyaudio.ogg.OggAudioDecoderProvider;

    uses be.twofold.tinyaudio.ogg.OggCodec;
}
