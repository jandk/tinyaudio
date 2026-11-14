package be.twofold.tinyaudio.vorbis.internal;

import be.twofold.tinyaudio.vorbis.VorbisException;

import java.io.IOException;
import java.nio.ByteBuffer;

public final class Identification {
    private static final int BYTES = 23;

    private final int channels;
    private final int sampleRate;
    private final int bitrateMaximum;
    private final int bitrateNominal;
    private final int bitrateMinimum;
    private final int blockSize0Log2;
    private final int blockSize1Log2;

    Identification(
        int channels,
        int sampleRate,
        int bitrateMaximum,
        int bitrateNominal,
        int bitrateMinimum,
        int blockSize0Log2,
        int blockSize1Log2
    ) {
        this.channels = channels;
        this.sampleRate = sampleRate;
        this.bitrateMaximum = bitrateMaximum;
        this.bitrateNominal = bitrateNominal;
        this.bitrateMinimum = bitrateMinimum;
        this.blockSize0Log2 = blockSize0Log2;
        this.blockSize1Log2 = blockSize1Log2;
    }

    public static Identification read(ByteBuffer buffer) throws IOException {
        if (buffer.remaining() < BYTES) {
            throw new VorbisException("Invalid identification header size: " + buffer.remaining());
        }
        int vorbisVersion = buffer.getInt();
        if (vorbisVersion != 0) {
            throw new VorbisException("Invalid vorbis version: " + vorbisVersion);
        }

        int channels = Byte.toUnsignedInt(buffer.get());
        if (channels == 0) {
            throw new VorbisException("Invalid channels: " + channels);
        }

        int sampleRate = buffer.getInt();
        if (sampleRate <= 0) {
            throw new VorbisException("Invalid sample rate: " + sampleRate);
        }

        int bitrateMaximum = buffer.getInt();
        int bitrateNominal = buffer.getInt();
        int bitrateMinimum = buffer.getInt();
        int blockSizes = buffer.get();
        int blockSize0Log2 = checkBlockSize(blockSizes & 0x0F);
        int blockSize1Log2 = checkBlockSize((blockSizes >> 4) & 0x0F);
        if (blockSize0Log2 > blockSize1Log2) {
            throw new VorbisException("Invalid block size: " + blockSize0Log2 + " > " + blockSize1Log2);
        }

        int framingFlag = buffer.get() & 0x01;
        if (framingFlag != 1) {
            throw new VorbisException("Invalid framing flag: " + framingFlag);
        }

        return new Identification(
            channels,
            sampleRate,
            bitrateMaximum,
            bitrateNominal,
            bitrateMinimum,
            blockSize0Log2,
            blockSize1Log2
        );
    }

    private static int checkBlockSize(int blockSizeLog2) throws IOException {
        if (blockSizeLog2 < 6 || blockSizeLog2 > 13) {
            throw new VorbisException("Invalid block size: " + (1 << blockSizeLog2));
        }
        return blockSizeLog2;
    }

    public int channels() {
        return channels;
    }

    public int sampleRate() {
        return sampleRate;
    }

    public int bitrateMaximum() {
        return bitrateMaximum;
    }

    public int bitrateNominal() {
        return bitrateNominal;
    }

    public int bitrateMinimum() {
        return bitrateMinimum;
    }

    public int blockSize0() {
        return 1 << blockSize0Log2;
    }

    public int blockSize1() {
        return 1 << blockSize1Log2;
    }
}
