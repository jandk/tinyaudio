package be.twofold.tinyaudio.vorbis;

import java.io.IOException;

final class Identification {
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

    static Identification read(BitReader bits) throws IOException {
        int vorbis_version = bits.read(32);
        if (vorbis_version != 0) {
            throw new VorbisException("Invalid vorbis version: " + vorbis_version);
        }

        int audioChannels = bits.read(8);
        if (audioChannels == 0) {
            throw new VorbisException("Invalid audio channels: " + audioChannels);
        }

        int audioSampleRate = bits.read(32);
        if (audioSampleRate <= 0) {
            throw new VorbisException("Invalid audio sample rate: " + audioSampleRate);
        }

        int bitrateMaximum = bits.read(32);
        int bitrateNominal = bits.read(32);
        int bitrateMinimum = bits.read(32);
        int blockSize0Log2 = readBlockSize(bits);
        int blockSize1Log2 = readBlockSize(bits);
        if (blockSize0Log2 > blockSize1Log2) {
            throw new VorbisException("Invalid block size: " + blockSize0Log2 + " > " + blockSize1Log2);
        }

        int framing_flag = bits.read(1);
        if (framing_flag != 1) {
            throw new VorbisException("Invalid framing flag: " + framing_flag);
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

    private static int readBlockSize(BitReader bits) throws IOException {
        int blockSizeLog2 = bits.read(4);
        if (blockSizeLog2 < 6 || blockSizeLog2 > 13) {
            throw new VorbisException("Invalid block size: " + (1 << blockSizeLog2));
        }
        return blockSizeLog2;
    }

    int audioChannels() {
        return audioChannels;
    }

    int audioSampleRate() {
        return audioSampleRate;
    }

    int bitrateMaximum() {
        return bitrateMaximum;
    }

    int bitrateNominal() {
        return bitrateNominal;
    }

    int bitrateMinimum() {
        return bitrateMinimum;
    }

    int blockSize0Log2() {
        return blockSize0Log2;
    }

    int blockSize1Log2() {
        return blockSize1Log2;
    }

    int blockSize0() {
        return 1 << blockSize0Log2;
    }

    int blockSize1() {
        return 1 << blockSize1Log2;
    }

    void write(BitOutputStream bits) throws IOException {
        bits.write(32, 0); // version
        bits.write(8, audioChannels);
        bits.write(32, audioSampleRate);
        bits.write(32, bitrateMaximum);
        bits.write(32, bitrateNominal);
        bits.write(32, bitrateMinimum);
        bits.write(4, blockSize0Log2);
        bits.write(4, blockSize1Log2);
        bits.writeBool(true);
    }
}
