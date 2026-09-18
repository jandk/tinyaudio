package be.twofold.tinyvorbis;

import java.io.IOException;

record Identification(
    int audioChannels,
    int audioSampleRate,
    int bitrateMaximum,
    int bitrateNominal,
    int bitrateMinimum,
    int blockSize0,
    int blockSize1
) {
    static Identification read(BitInputStream bits) throws IOException {
        int vorbis_version = bits.read(32);
        if (vorbis_version != 0) {
            throw new VorbisException("Invalid vorbis version: " + vorbis_version);
        }

        int audioChannels = bits.read(8);
        if (audioChannels <= 0) {
            throw new VorbisException("Invalid audio channels: " + audioChannels);
        }

        int audioSampleRate = bits.read(32);
        if (audioSampleRate <= 0) {
            throw new VorbisException("Invalid audio sample rate: " + audioSampleRate);
        }

        int bitrateMaximum = bits.read(32);
        int bitrateNominal = bits.read(32);
        int bitrateMinimum = bits.read(32);
        int blockSize0 = readBlockSize(bits);
        int blockSize1 = readBlockSize(bits);
        if (blockSize0 > blockSize1) {
            throw new VorbisException("Invalid block size: " + blockSize0 + " > " + blockSize1);
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
            blockSize0,
            blockSize1
        );
    }

    private static int readBlockSize(BitInputStream bits) throws IOException {
        int blockSizeLog2 = bits.read(4);
        if (blockSizeLog2 < 6 || blockSizeLog2 > 13) {
            throw new VorbisException("Invalid block size: " + (1 << blockSizeLog2));
        }
        return 1 << blockSizeLog2;
    }

    void write(BitOutputStream bits) throws IOException {
        bits.write(audioChannels, 8);
        bits.write(audioSampleRate, 32);
        bits.write(bitrateMaximum, 32);
        bits.write(bitrateNominal, 32);
        bits.write(bitrateMinimum, 32);
        bits.write(Integer.numberOfTrailingZeros(blockSize0), 4);
        bits.write(Integer.numberOfTrailingZeros(blockSize1), 4);
        bits.writeBool(true);
    }
}
