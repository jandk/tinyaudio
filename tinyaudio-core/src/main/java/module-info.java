module be.twofold.tinyaudio.core {
    requires static java.desktop;

    exports be.twofold.tinyaudio.core;

    uses be.twofold.tinyaudio.core.AudioDecoderProvider;

    provides javax.sound.sampled.spi.AudioFileReader
        with be.twofold.tinyaudio.core.spi.TinyAudioFileReader;

    // For testing in IJ
    opens be.twofold.tinyaudio.core to org.junit.platform.commons;
}
