module be.twofold.tinyflac {
    requires java.desktop;

    exports be.twofold.tinyflac;
    exports be.twofold.tinyflac.metadata;

    provides javax.sound.sampled.spi.AudioFileReader
        with be.twofold.tinyflac.spi.FlacAudioFileReader;
    provides javax.sound.sampled.spi.FormatConversionProvider
        with be.twofold.tinyflac.spi.FlacFormatConversionProvider;

    // For testing in IJ
    opens be.twofold.tinyflac to org.junit.platform.commons;
}
