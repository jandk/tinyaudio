module be.twofold.tinyaudio.core {
    requires static java.desktop;

    exports be.twofold.tinyaudio.core;
    exports be.twofold.tinyaudio.core.internal to
        be.twofold.tinyaudio.flac,
        be.twofold.tinyaudio.ogg,
        be.twofold.tinyaudio.opus,
        be.twofold.tinyaudio.vorbis;

    uses be.twofold.tinyaudio.core.AudioDecoderProvider;

    provides javax.sound.sampled.spi.AudioFileReader
        with be.twofold.tinyaudio.core.spi.TinyAudioFileReader;
}
