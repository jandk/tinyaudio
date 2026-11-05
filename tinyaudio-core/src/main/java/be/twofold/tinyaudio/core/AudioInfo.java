package be.twofold.tinyaudio.core;

import java.util.Objects;

public final class AudioInfo {
    private final String codec;
    private final int sampleRate;
    private final int channels;
    private final long totalFrames;

    public AudioInfo(String codec, int sampleRate, int channels, long totalFrames) {
        this.codec = Objects.requireNonNull(codec);
        this.sampleRate = sampleRate;
        this.channels = channels;
        this.totalFrames = totalFrames;
    }

    public String codec() {
        return codec;
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
            "codec='" + codec + "', " +
            "sampleRate=" + sampleRate + ", " +
            "channels=" + channels + ", " +
            "totalFrames=" + totalFrames +
            "}";
    }
}
