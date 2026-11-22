package be.twofold.tinyaudio.core;

import java.util.Objects;

/**
 * The properties of an audio stream, as returned by {@link AudioDecoder#info()}.
 *
 * <p>A frame is one sample for each channel, so one second of audio is {@link #sampleRate()} frames.
 */
public final class AudioInfo {
    private final String container;
    private final String codec;
    private final String extension;
    private final int sampleRate;
    private final int channels;
    private final long totalFrames;

    /**
     * Creates the properties of a stream.
     *
     * @param container   the container format
     * @param codec       the codec
     * @param extension   the usual file extension, without the dot
     * @param sampleRate  the sample rate in Hz
     * @param channels    the number of channels
     * @param totalFrames the length in frames, or {@code -1} if unknown
     */
    public AudioInfo(
        String container,
        String codec,
        String extension,
        int sampleRate,
        int channels,
        long totalFrames
    ) {
        this.container = Objects.requireNonNull(container);
        this.codec = Objects.requireNonNull(codec);
        this.extension = Objects.requireNonNull(extension);
        this.sampleRate = sampleRate;
        this.channels = channels;
        this.totalFrames = totalFrames;
    }

    /**
     * Returns the container format: {@code "flac"} for native FLAC, {@code "ogg"} for anything in Ogg.
     *
     * @return the container format
     */
    public String container() {
        return container;
    }

    /**
     * Returns the codec: {@code "flac"}, {@code "vorbis"} or {@code "opus"}.
     *
     * @return the codec
     */
    public String codec() {
        return codec;
    }

    /**
     * Returns the usual file extension, without the dot: {@code "flac"}, {@code "oga"} for FLAC in Ogg,
     * {@code "ogg"} for Vorbis, or {@code "opus"}.
     *
     * @return the file extension
     */
    public String extension() {
        return extension;
    }

    /**
     * Returns the sample rate in Hz. Opus always decodes at 48000 Hz, whatever rate it was encoded from.
     *
     * @return the sample rate
     */
    public int sampleRate() {
        return sampleRate;
    }

    /**
     * Returns the number of channels, which is also the number of arrays {@link AudioDecoder#read} fills.
     *
     * @return the number of channels
     */
    public int channels() {
        return channels;
    }

    /**
     * Returns the length in frames, or {@code -1} if unknown. FLAC usually stores the length in its header. Ogg
     * streams only have it at the end, so it is always unknown for those.
     *
     * @return the length in frames, or {@code -1}
     */
    public long totalFrames() {
        return totalFrames;
    }

    @Override
    public String toString() {
        return "AudioInfo{" +
            "container='" + container + "', " +
            "codec='" + codec + "', " +
            "extension='" + extension + "', " +
            "sampleRate=" + sampleRate + ", " +
            "channels=" + channels + ", " +
            "totalFrames=" + totalFrames +
            "}";
    }
}
