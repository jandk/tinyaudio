module be.twofold.tinyogg {
    requires be.twofold.tinyaudio.core;

    exports be.twofold.tinyogg;

    uses be.twofold.tinyogg.OggCodec;

    // For testing in IJ
    opens be.twofold.tinyogg to org.junit.platform.commons;
}
