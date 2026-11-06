package be.twofold.tinyaudio.flac.metadata;

import be.twofold.tinyaudio.core.internal.IOUtils;
import be.twofold.tinyaudio.flac.FlacException;

import java.nio.ByteBuffer;

public final class StreamInfo implements MetadataBlock {
    public static final int BYTES = 34;

    private final int minBlockSize;
    private final int maxBlockSize;
    private final int minFrameSize;
    private final int maxFrameSize;
    private final int sampleRate;
    private final byte channels;
    private final byte bitsPerSample;
    private final long totalSamples;
    private final byte[] md5Sum;

    public StreamInfo(
        int minBlockSize,
        int maxBlockSize,
        int minFrameSize,
        int maxFrameSize,
        int sampleRate,
        byte channels,
        byte bitsPerSample,
        long totalSamples,
        byte[] md5Sum
    ) throws FlacException {
        if (sampleRate == 0) {
            throw new FlacException("Invalid sample rate");
        }
        this.minBlockSize = minBlockSize;
        this.maxBlockSize = maxBlockSize;
        this.minFrameSize = minFrameSize;
        this.maxFrameSize = maxFrameSize;
        this.sampleRate = sampleRate;
        this.channels = channels;
        this.bitsPerSample = bitsPerSample;
        this.totalSamples = totalSamples;
        this.md5Sum = md5Sum.clone();
    }

    public static StreamInfo read(ByteBuffer buffer) throws FlacException {
        int minBlockSize = Short.toUnsignedInt(buffer.getShort());
        int maxBlockSize = Short.toUnsignedInt(buffer.getShort());
        int minFrameSize = getInt24(buffer);
        int maxFrameSize = getInt24(buffer);

        long rest = buffer.getLong();
        int sampleRate = (int) (rest >>> 44);
        byte channels = (byte) (((rest >>> 41) & 0x07) + 1);
        byte bitsPerSample = (byte) (((rest >>> 36) & 0x1F) + 1);
        long totalSamples = rest & 0x000F_FFFF_FFFFL;
        byte[] md5Sum = IOUtils.getBytes(buffer, 16);

        return new StreamInfo(
            minBlockSize,
            maxBlockSize,
            minFrameSize,
            maxFrameSize,
            sampleRate,
            channels,
            bitsPerSample,
            totalSamples,
            md5Sum
        );
    }

    private static int getInt24(ByteBuffer buffer) {
        int b0 = Byte.toUnsignedInt(buffer.get());
        int b1 = Byte.toUnsignedInt(buffer.get());
        int b2 = Byte.toUnsignedInt(buffer.get());
        return b0 << 16 | b1 << 8 | b2;
    }

    public int minBlockSize() {
        return minBlockSize;
    }

    public int maxBlockSize() {
        return maxBlockSize;
    }

    public int minFrameSize() {
        return minFrameSize;
    }

    public int maxFrameSize() {
        return maxFrameSize;
    }

    public int sampleRate() {
        return sampleRate;
    }

    public byte channels() {
        return channels;
    }

    public byte bitsPerSample() {
        return bitsPerSample;
    }

    public long totalSamples() {
        return totalSamples;
    }

    public byte[] md5Sum() {
        return md5Sum.clone();
    }
}
