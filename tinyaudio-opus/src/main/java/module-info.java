/**
 * Opus decoding, in Ogg, including multichannel streams. Only CELT is supported: reading a SILK or hybrid packet
 * throws {@link java.lang.UnsupportedOperationException}.
 */
module be.twofold.tinyaudio.opus {
    requires be.twofold.tinyaudio.core;
    requires be.twofold.tinyaudio.ogg;
    requires java.xml;

    exports be.twofold.tinyaudio.opus;

    provides be.twofold.tinyaudio.ogg.internal.OggCodec
        with be.twofold.tinyaudio.opus.OpusOggCodec;
}
