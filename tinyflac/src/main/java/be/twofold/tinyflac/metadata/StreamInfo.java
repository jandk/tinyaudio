package be.twofold.tinyflac.metadata;

import be.twofold.tinyflac.internal.IOUtils;

import java.io.DataInputStream;
import java.io.IOException;

public record StreamInfo(
    int minBlockSize,
    int maxBlockSize,
    int minFrameSize,
    int maxFrameSize,
    int sampleRate,
    int channels,
    int bitsPerSample,
    long totalSamples,
    byte[] md5Sum
) implements MetadataBlock {
    public static final int BYTES = 34;

    public StreamInfo {
        md5Sum = md5Sum.clone();
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

    @Override
    public byte[] md5Sum() {
        return md5Sum.clone();
    }
}
