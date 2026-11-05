module be.twofold.tinyaudio.flac {
    requires be.twofold.tinyaudio.core;

    exports be.twofold.tinyaudio.flac;
    exports be.twofold.tinyaudio.flac.metadata;

    provides be.twofold.tinyaudio.core.AudioDecoderProvider
        with be.twofold.tinyaudio.flac.FlacAudioDecoderProvider;

    // For testing in IJ
    opens be.twofold.tinyaudio.flac to org.junit.platform.commons;
}
