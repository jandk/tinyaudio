/**
 * Ogg container support. The codec modules plug in to it, so it is needed for Vorbis, Opus and FLAC in Ogg.
 */
module be.twofold.tinyaudio.ogg {
    requires be.twofold.tinyaudio.core;

    exports be.twofold.tinyaudio.ogg;

    exports be.twofold.tinyaudio.ogg.internal to
        be.twofold.tinyaudio.flac,
        be.twofold.tinyaudio.opus,
        be.twofold.tinyaudio.vorbis;

    provides be.twofold.tinyaudio.core.AudioDecoderProvider
        with be.twofold.tinyaudio.ogg.OggAudioDecoderProvider;

    uses be.twofold.tinyaudio.ogg.internal.OggCodec;
}
