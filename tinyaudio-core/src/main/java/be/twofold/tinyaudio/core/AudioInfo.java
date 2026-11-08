package be.twofold.tinyaudio.core;

import java.util.Objects;

public final class AudioInfo {
    private final String container;
    private final String codec;
    private final String extension;
    private final int sampleRate;
    private final int channels;
    private final long totalFrames;

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

    public String container() {
        return container;
    }

    public String codec() {
        return codec;
    }

    public String extension() {
        return extension;
    }

    public int sampleRate() {
        return sampleRate;
    }

    public int channels() {
        return channels;
    }

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
