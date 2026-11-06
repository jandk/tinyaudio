module be.twofold.tinyaudio.flac {
    requires be.twofold.tinyaudio.core;

    exports be.twofold.tinyaudio.flac;

    provides be.twofold.tinyaudio.core.AudioDecoderProvider
        with be.twofold.tinyaudio.flac.FlacAudioDecoderProvider;
}
