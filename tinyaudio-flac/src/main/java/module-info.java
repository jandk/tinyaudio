module be.twofold.tinyaudio.flac {
    requires be.twofold.tinyaudio.core;
    requires be.twofold.tinyaudio.ogg;

    exports be.twofold.tinyaudio.flac;

    provides be.twofold.tinyaudio.core.AudioDecoderProvider
        with be.twofold.tinyaudio.flac.FlacAudioDecoderProvider;
    provides be.twofold.tinyaudio.ogg.internal.OggCodec
        with be.twofold.tinyaudio.flac.FlacOggCodec;
}
