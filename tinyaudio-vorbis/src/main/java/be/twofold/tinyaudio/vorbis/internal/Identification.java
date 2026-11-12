package be.twofold.tinyaudio.vorbis.internal;

import be.twofold.tinyaudio.vorbis.VorbisException;

import java.io.IOException;
import java.nio.ByteBuffer;

public final class Identification {
    private static final int BYTES = 23;

    private final int audioChannels;
    private final int audioSampleRate;
    private final int bitrateMaximum;
    private final int bitrateNominal;
    private final int bitrateMinimum;
    private final int blockSize0Log2;
    private final int blockSize1Log2;

    Identification(
        int audioChannels,
        int audioSampleRate,
        int bitrateMaximum,
        int bitrateNominal,
        int bitrateMinimum,
        int blockSize0Log2,
        int blockSize1Log2
    ) {
        this.audioChannels = audioChannels;
        this.audioSampleRate = audioSampleRate;
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

        int audioChannels = Byte.toUnsignedInt(buffer.get());
        if (audioChannels == 0) {
            throw new VorbisException("Invalid audio channels: " + audioChannels);
        }

        int audioSampleRate = buffer.getInt();
        if (audioSampleRate <= 0) {
            throw new VorbisException("Invalid audio sample rate: " + audioSampleRate);
        }

        int bitrateMaximum = buffer.getInt();
        int bitrateNominal = buffer.getInt();
        int bitrateMinimum = buffer.getInt();
        int blockSize = buffer.get();
        int blockSize0Log2 = checkBlockSize(blockSize & 0x0F);
        int blockSize1Log2 = checkBlockSize((blockSize >> 4) & 0x0F);
        if (blockSize0Log2 > blockSize1Log2) {
            throw new VorbisException("Invalid block size: " + blockSize0Log2 + " > " + blockSize1Log2);
        }

        int framingFlag = buffer.get() & 0x01;
        if (framingFlag != 1) {
            throw new VorbisException("Invalid framing flag: " + framingFlag);
        }

        return new Identification(
            audioChannels,
            audioSampleRate,
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

    public int audioChannels() {
        return audioChannels;
    }

    public int audioSampleRate() {
        return audioSampleRate;
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
