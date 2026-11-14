package be.twofold.tinyaudio.flac.internal;

import be.twofold.tinyaudio.core.internal.BitReader;
import be.twofold.tinyaudio.flac.FlacException;
import be.twofold.tinyaudio.flac.metadata.StreamInfo;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Objects;

public final class FlacDecoder {
    private static final int[][] FIXED = {
        {},
        {1},
        {2, -1},
        {3, -3, 1},
        {4, -6, 4, -1}
    };

    private final StreamInfo streamInfo;
    private int[] coefficients;
    private int rightShift;
    private long sampleCount;

    public FlacDecoder(StreamInfo streamInfo) {
        this.streamInfo = Objects.requireNonNull(streamInfo);
        if (streamInfo.bitsPerSample() >= 32) {
            throw new UnsupportedOperationException("Unsupported bits per sample: " + streamInfo.bitsPerSample());
        }
    }

    public int decodeFrame(byte[] data, int off, int len, boolean finished, int[][] pcm) throws FlacException {
        ByteBuffer buffer = ByteBuffer.wrap(data, off, len);
        FlacFrameHeader header = FlacFrameHeader.read(buffer);
        int blockSize = header.blockSize();

        long total = streamInfo.totalSamples();
        boolean last = total == 0 || sampleCount + blockSize >= total || finished;
        validateFrameHeader(header, last);
        sampleCount += blockSize;

        BitReader bits = new BitReader(buffer.array(), buffer.position(), buffer.remaining(), ByteOrder.BIG_ENDIAN);
        for (int ch = 0; ch < header.numberOfChannels(); ch++) {
            FlacSubFrameHeader subFrameHeader = FlacSubFrameHeader.read(bits);

            boolean side = header.channelCorrelation() != null && header.channelCorrelation().side() == ch;
            int bitsPerSample = header.bitsPerSample() - subFrameHeader.getWastedBits() + (side ? 1 : 0);

            switch (subFrameHeader.getSubframeType()) {
                case CONSTANT:
                    decodeConstant(bits, pcm[ch], blockSize, bitsPerSample);
                    break;
                case VERBATIM:
                    decodeVerbatim(bits, pcm[ch], blockSize, bitsPerSample);
                    break;
                case FIXED:
                    decodeFixed(bits, pcm[ch], blockSize, bitsPerSample, subFrameHeader.getOrder());
                    break;
                case LPC:
                    decodeLpc(bits, pcm[ch], blockSize, bitsPerSample, subFrameHeader.getOrder());
                    break;
            }

            applyWastedBits(pcm[ch], blockSize, subFrameHeader.getWastedBits());
        }
        if (bits.isOverrun()) {
            throw new FlacException("Frame data truncated");
        }
        if (bits.bitsRemaining() >= 24 && !finished) {
            throw new FlacException("Packet contains more than one frame");
        }

        decorrelate(pcm, header.channelCorrelation(), blockSize);
        return blockSize;
    }

    private void decodeConstant(BitReader bits, int[] samples, int blockSize, int bitsPerSample) {
        int value = bits.readSigned(bitsPerSample);
        Arrays.fill(samples, 0, blockSize, value);
    }

    private void decodeVerbatim(BitReader bits, int[] samples, int blockSize, int bitsPerSample) {
        readInts(bits, samples, 0, blockSize, bitsPerSample);
    }

    private void decodeFixed(BitReader bits, int[] samples, int blockSize, int bitsPerSample, int order) throws FlacException {
        rightShift = 0;
        coefficients = FIXED[order];
        readInts(bits, samples, 0, order, bitsPerSample);
        decodeCodedResidual(bits, samples, blockSize, order);
    }

    private void decodeLpc(BitReader bits, int[] samples, int blockSize, int bitsPerSample, int order) throws FlacException {
        readInts(bits, samples, 0, order, bitsPerSample);

        int precision = bits.read(4) + 1;
        if (precision == 16) {
            throw new FlacException("precision 16");
        }

        rightShift = bits.readSigned(5);
        coefficients = new int[order];
        readInts(bits, coefficients, 0, order, precision);
        decodeCodedResidual(bits, samples, blockSize, order);
    }

    private void decodeCodedResidual(BitReader bits, int[] samples, int blockSize, int order) throws FlacException {
        int codingMethod = bits.read(2);
        if (codingMethod > 1) {
            throw new FlacException("Invalid coding method: " + codingMethod);
        }
        codingMethod += 4;

        int partitionOrder = bits.read(4);
        int partitionCount = 1 << partitionOrder;
        int samplesPerPartition = blockSize >> partitionOrder;
        if ((blockSize & (partitionCount - 1)) != 0 || samplesPerPartition < order) {
            throw new FlacException("Invalid residual partition order: " + partitionOrder);
        }

        int offset = order;
        for (int p = 0; p < partitionCount; p++) {
            int count = samplesPerPartition - (p == 0 ? order : 0);
            decodePartition(bits, samples, offset, count, codingMethod);
            offset += count;
        }

        reconstructSamples(samples, blockSize, order);
    }

    private void decodePartition(BitReader bits, int[] samples, int off, int len, int codingMethod) {
        int riceParameter = bits.read(codingMethod);
        if (riceParameter == (1 << codingMethod) - 1) {
            int residualBits = bits.read(5);
            readInts(bits, samples, off, len, residualBits);
        } else {
            for (int i = 0; i < len; i++) {
                samples[off++] = decodeRice(bits, riceParameter);
            }
        }
    }

    private void reconstructSamples(int[] samples, int blockSize, int order) {
        int[] coefficients = this.coefficients;
        for (int i = order; i < blockSize; i++) {
            long sum = 0;
            for (int j = 0; j < order; j++) {
                sum += (long) samples[i - j - 1] * coefficients[j];
            }
            samples[i] += (int) (sum >> rightShift);
        }
    }

    private void applyWastedBits(int[] samples, int blockSize, int wastedBits) {
        for (int i = 0; i < blockSize; i++) {
            samples[i] <<= wastedBits;
        }
    }

    private void decorrelate(int[][] samples, FlacFrameHeader.ChannelType type, int blockSize) {
        if (type == null || type == FlacFrameHeader.ChannelType.LEFT_RIGHT) {
            return;
        }

        int[] c0 = samples[0];
        int[] c1 = samples[1];

        switch (type) {
            case LEFT_SIDE:
                for (int i = 0; i < blockSize; i++) {
                    int side = c1[i];
                    c1[i] = c0[i] - side;
                }
                break;
            case SIDE_RIGHT:
                for (int i = 0; i < blockSize; i++) {
                    int side = c0[i];
                    c0[i] = c1[i] + side;
                }
                break;
            case MID_SIDE:
                for (int i = 0; i < blockSize; i++) {
                    int mid = c0[i];
                    int side = c1[i];

                    mid = mid << 1 | side & 1;
                    c0[i] = (mid + side) >> 1;
                    c1[i] = (mid - side) >> 1;
                }
                break;
        }
    }

    private int decodeRice(BitReader bits, int riceParameter) {
        int msb = bits.readUnary();
        int lsb = bits.read(riceParameter);
        int folded = msb << riceParameter | lsb;
        return (folded >>> 1) ^ -(folded & 1);
    }

    private void readInts(BitReader bits, int[] dst, int off, int len, int bitCount) {
        Objects.checkFromIndexSize(off, len, dst.length);
        for (int i = off, lim = off + len; i < lim; i++) {
            dst[i] = bits.readSigned(bitCount);
        }
    }

    private void validateFrameHeader(FlacFrameHeader header, boolean last) throws FlacException {
        int minimumBlockSize = streamInfo.minBlockSize();
        int maximumBlockSize = streamInfo.maxBlockSizeOrDefault();
        int blockSize = header.blockSize();

        boolean checkMin = minimumBlockSize > 0 && !last;
        if (checkMin && blockSize < minimumBlockSize || blockSize > maximumBlockSize) {
            throw new FlacException("Block size (" + blockSize + ") not between " + minimumBlockSize + " and " + maximumBlockSize);
        }
        if (header.sampleRate() != streamInfo.sampleRate()) {
            throw new FlacException("Sample rate (" + header.sampleRate() + ") not " + streamInfo.sampleRate());
        }
        if (header.numberOfChannels() != streamInfo.channels()) {
            throw new FlacException("Channel count (" + header.numberOfChannels() + ") not " + streamInfo.channels());
        }
        if (header.bitsPerSample() != streamInfo.bitsPerSample()) {
            throw new FlacException("Bit depth (" + header.bitsPerSample() + ") not " + streamInfo.bitsPerSample());
        }
    }
}
