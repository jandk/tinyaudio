package be.twofold.tinyaudio.flac.metadata;

import be.twofold.tinyaudio.core.internal.IOUtils;

import java.io.DataInputStream;
import java.io.IOException;

public final class StreamInfo implements MetadataBlock {
    public static final int BYTES = 34;

    private final int minBlockSize;
    private final int maxBlockSize;
    private final int minFrameSize;
    private final int maxFrameSize;
    private final int sampleRate;
    private final int channels;
    private final int bitsPerSample;
    private final long totalSamples;
    private final byte[] md5Sum;

    public StreamInfo(
        int minBlockSize,
        int maxBlockSize,
        int minFrameSize,
        int maxFrameSize,
        int sampleRate,
        int channels,
        int bitsPerSample,
        long totalSamples,
        byte[] md5Sum
    ) {
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

    public static StreamInfo read(DataInputStream dis) throws IOException {
        int minBlockSize = dis.readUnsignedShort();
        int maxBlockSize = dis.readUnsignedShort();
        int minFrameSize = dis.readUnsignedByte() << 16 | dis.readUnsignedByte() << 8 | dis.readUnsignedByte();
        int maxFrameSize = dis.readUnsignedByte() << 16 | dis.readUnsignedByte() << 8 | dis.readUnsignedByte();

        long rest = dis.readLong();
        int sampleRate = (int) (rest >>> 44);
        int channels = (int) ((rest >>> 41) & 0x07) + 1;
        int bitsPerSample = (int) ((rest >>> 36) & 0x1F) + 1;
        long totalSamples = rest & 0x000F_FFFF_FFFFL;
        byte[] md5Sum = IOUtils.readBytes(dis, 16);

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

    public int getMinBlockSize() {
        return minBlockSize;
    }

    public int getMaxBlockSize() {
        return maxBlockSize;
    }

    public int getMinFrameSize() {
        return minFrameSize;
    }

    public int getMaxFrameSize() {
        return maxFrameSize;
    }

    public int getSampleRate() {
        return sampleRate;
    }

    public int getChannels() {
        return channels;
    }

    public int getBitsPerSample() {
        return bitsPerSample;
    }

    public long getTotalSamples() {
        return totalSamples;
    }

    public byte[] getMd5Sum() {
        return md5Sum.clone();
    }
}
