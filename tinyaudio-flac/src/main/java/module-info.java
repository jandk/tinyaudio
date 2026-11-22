/**
 * FLAC decoding, native and in Ogg, up to 24 bits per sample. Opening a stream with more bits throws
 * {@link be.twofold.tinyaudio.core.UnsupportedFormatException}.
 */
module be.twofold.tinyaudio.flac {
    requires be.twofold.tinyaudio.core;
    requires be.twofold.tinyaudio.ogg;

    exports be.twofold.tinyaudio.flac;

    provides be.twofold.tinyaudio.core.AudioDecoderProvider
        with be.twofold.tinyaudio.flac.FlacAudioDecoderProvider;
    provides be.twofold.tinyaudio.ogg.internal.OggCodec
        with be.twofold.tinyaudio.flac.FlacOggCodec;
}
