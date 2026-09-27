module be.twofold.tinyflac {
    requires be.twofold.tinyaudio.core;
    requires java.desktop;

    exports be.twofold.tinyaudio.flac;
    exports be.twofold.tinyaudio.flac.metadata;

    provides javax.sound.sampled.spi.AudioFileReader
            with be.twofold.tinyaudio.flac.spi.FlacAudioFileReader;
    provides javax.sound.sampled.spi.FormatConversionProvider
            with be.twofold.tinyaudio.flac.spi.FlacFormatConversionProvider;

    // For testing in IJ
    opens be.twofold.tinyaudio.flac to org.junit.platform.commons;
}
