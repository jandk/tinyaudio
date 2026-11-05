package be.twofold.tinyaudio.flac;

import be.twofold.tinyaudio.core.AudioInfo;
import be.twofold.tinyaudio.core.BlockAudioDecoder;
import be.twofold.tinyaudio.core.CRC;
import be.twofold.tinyaudio.flac.metadata.StreamInfo;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Objects;
import java.util.zip.CheckedInputStream;

public class FlacDecoder extends BlockAudioDecoder {
    private static final int[][] FIXED = {
        {},
        {1},
        {2, -1},
        {3, -3, 1},
        {4, -6, 4, -1}
    };

    private final InputStream in;
    private final StreamInfo streamInfo;

    private final int[][] samples;
    private int[] coefficients;
    private int rightShift;
    private long sampleCount;

    public FlacDecoder(InputStream in, StreamInfo streamInfo) {
        super(streamInfo.getChannels(), maximumBlockSize(streamInfo));

        this.in = Objects.requireNonNull(in);
        this.streamInfo = Objects.requireNonNull(streamInfo);
        this.samples = new int[streamInfo.getChannels()][maximumBlockSize(streamInfo)];
    }

    private static int maximumBlockSize(StreamInfo streamInfo) {
        int size = streamInfo.getMaxBlockSize();
        return size == 0 ? 65535 : size;
    }

    @Override
    public AudioInfo info() {
        long total = streamInfo.getTotalSamples();
        return new AudioInfo(
            "flac",
            streamInfo.getSampleRate(),
            streamInfo.getChannels(),
            total == 0 ? -1 : total
        );
    }

    @Override
    protected int decodeBlock(short[][] pcm) throws IOException {
        CheckedInputStream checked = new CheckedInputStream(in, CRC.FLAC_FRAME.get());

        FlacFrameHeader frameHeader = FlacFrameHeader.read(checked);
        if (frameHeader == null) {
            return -1;
        }

        long total = streamInfo.getTotalSamples();
        boolean last = total == 0 || sampleCount + frameHeader.getBlockSize() >= total;
        validateFrameHeader(frameHeader, streamInfo, last);
        int blocksize = frameHeader.getBlockSize();

        BitInputStream bits = new BitInputStream(checked);
        for (int i = 0; i < frameHeader.getNumberOfChannels(); i++) {
            FlacSubFrameHeader subFrameHeader = FlacSubFrameHeader.read(bits);

            boolean side = frameHeader.getChannelCorrelation() != null
                && frameHeader.getChannelCorrelation().getSide() == i;
            int bitsPerSample = frameHeader.getBitsPerSample() - subFrameHeader.getWastedBits() + (side ? 1 : 0);

            switch (subFrameHeader.getSubframeType()) {
                case CONSTANT:
                    decodeConstant(bits, blocksize, bitsPerSample, i);
                    break;
                case VERBATIM:
                    decodeVerbatim(bits, blocksize, bitsPerSample, i);
                    break;
                case FIXED:
                    decodeFixed(bits, blocksize, bitsPerSample, subFrameHeader.getOrder(), i);
                    break;
                case LPC:
                    decodeLpc(bits, blocksize, bitsPerSample, subFrameHeader.getOrder(), i);
                    break;
            }

            if (subFrameHeader.getWastedBits() > 0) {
                applyWastedBits(samples[i], blocksize, subFrameHeader.getWastedBits());
            }

        }

        short actualCrc = (short) checked.getChecksum().getValue();
        short expectedCrc = (short) (checked.read() << 8 | checked.read());
        if (actualCrc != expectedCrc) {
            throw new IOException("CRC mismatch");
        }

        sampleCount += blocksize;
        decorrelate(frameHeader.getChannelCorrelation(), blocksize);
        convertToShort(pcm, blocksize);
        return blocksize;
    }

    @Override
    public void close() throws IOException {
        in.close();
    }

    private void applyWastedBits(int[] samples, int blockSize, int wastedBits) {
        if (wastedBits == 0) {
            return;
        }
        for (int i = 0; i < blockSize; i++) {
            samples[i] <<= wastedBits;
        }
    }

    private void decorrelate(FlacFrameHeader.ChannelType type, int blocksize) {
        if (type == null || type == FlacFrameHeader.ChannelType.LEFT_RIGHT) {
            return;
        }

        int[] c0 = samples[0];
        int[] c1 = samples[1];

        switch (type) {
            case LEFT_SIDE:
                for (int i = 0; i < blocksize; i++) {
                    int side = c1[i];
                    c1[i] = c0[i] - side;
                }
                break;
            case SIDE_RIGHT:
                for (int i = 0; i < blocksize; i++) {
                    int side = c0[i];
                    c0[i] = c1[i] + side;
                }
                break;
            case MID_SIDE:
                for (int i = 0; i < blocksize; i++) {
                    int mid = c0[i];
                    int side = c1[i];

                    mid = mid << 1 | side & 1;
                    c0[i] = (mid + side) >> 1;
                    c1[i] = (mid - side) >> 1;
                }
                break;
        }
    }

    private void convertToShort(short[][] pcm, int blocksize) {
        int shift = streamInfo.getBitsPerSample() - 16;
        for (int ch = 0; ch < pcm.length; ch++) {
            int[] src = samples[ch];
            short[] dst = pcm[ch];
            if (shift >= 0) {
                for (int i = 0; i < blocksize; i++) {
                    dst[i] = (short) (src[i] >> shift);
                }
            } else {
                for (int i = 0; i < blocksize; i++) {
                    dst[i] = (short) (src[i] << -shift);
                }
            }
        }
    }

    private void decodeConstant(BitInputStream bits, int blocksize, int bitsPerSample, int channel) throws IOException {
        int value = bits.readSigned(bitsPerSample);
        Arrays.fill(samples[channel], 0, blocksize, value);
    }

    private void decodeVerbatim(BitInputStream bits, int blocksize, int bitsPerSample, int channel) throws IOException {
        readInts(bits, bitsPerSample, samples[channel], 0, blocksize);
    }

    private void decodeFixed(BitInputStream bits, int blocksize, int bitsPerSample, int order, int channel) throws IOException {
        rightShift = 0;
        coefficients = FIXED[order];
        readInts(bits, bitsPerSample, samples[channel], 0, order);
        decodeCodedResidual(bits, blocksize, order, channel);
    }

    private void decodeLpc(BitInputStream bits, int blocksize, int bitsPerSample, int order, int channel) throws IOException {
        readInts(bits, bitsPerSample, samples[channel], 0, order);

        int precision = bits.read(4) + 1;
        if (precision == 16) {
            throw new IOException("precision 16");
        }

        rightShift = bits.readSigned(5);
        coefficients = new int[order];
        readInts(bits, precision, coefficients, 0, order);
        decodeCodedResidual(bits, blocksize, order, channel);
    }

    private void decodeCodedResidual(BitInputStream bits, int blockSize, int order, int channel) throws IOException {
        int codingMethod;
        switch (bits.read(2)) {
            case 0:
                codingMethod = 4;
                break;
            case 1:
                codingMethod = 5;
                break;
            default:
                throw new IOException("Invalid coding method");
        }

        int partitionOrder = bits.read(4);
        int partitionCount = 1 << partitionOrder;
        int samplesPerPartition = blockSize >> partitionOrder;

        int offset = order;
        for (int p = 0; p < partitionCount; p++) {
            int sampleCount = samplesPerPartition - (p == 0 ? order : 0);
            decodePartition(bits, codingMethod, sampleCount, samples[channel], offset);
            offset += sampleCount;
        }

        reconstructSamples(samples[channel], blockSize, order);
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

    private void decodePartition(BitInputStream bits, int codingMethod, int sampleCount, int[] target, int offset) throws IOException {
        int riceParameter = bits.read(codingMethod);
        if (riceParameter == (1 << codingMethod) - 1) {
            int residualBits = bits.read(5);
            readInts(bits, residualBits, target, offset, sampleCount);
        } else {
            for (int i = 0; i < sampleCount; i++) {
                target[offset++] = decodeRice(bits, riceParameter);
            }
        }
    }

    private int decodeRice(BitInputStream bits, int riceParameter) throws IOException {
        int msb = bits.readUnary();
        int lsb = bits.read(riceParameter);
        int folded = msb << riceParameter | lsb;
        return (folded >>> 1) ^ -(folded & 1);
    }

    private void readInts(BitInputStream bits, int bitCount, int[] target, int offset, int count) throws IOException {
        Objects.checkFromIndexSize(offset, count, target.length);
        for (int i = offset, lim = offset + count; i < lim; i++) {
            target[i] = bits.readSigned(bitCount);
        }
    }

    private void validateFrameHeader(FlacFrameHeader frameHeader, StreamInfo streamInfo, boolean last) {
        int minimumBlockSize = streamInfo.getMinBlockSize();
        int maximumBlockSize = streamInfo.getMaxBlockSize();
        int blockSize = frameHeader.getBlockSize();

        boolean checkMinimum = minimumBlockSize > 0 && !last;
        if (checkMinimum && blockSize < minimumBlockSize ||
            maximumBlockSize > 0 && blockSize > maximumBlockSize) {
            throw new IllegalArgumentException(String.format(
                "Block size (%s) not between %s and %s",
                blockSize, minimumBlockSize, maximumBlockSize));
        }
        if (frameHeader.getSampleRate() != streamInfo.getSampleRate()) {
            throw new IllegalArgumentException(String.format("Sample rate (%s) not %s",
                frameHeader.getSampleRate(), streamInfo.getSampleRate()));
        }
        if (frameHeader.getNumberOfChannels() != streamInfo.getChannels()) {
            throw new IllegalArgumentException(String.format("Channel count (%s) not %s",
                frameHeader.getNumberOfChannels(), streamInfo.getChannels()));
        }
        if (frameHeader.getBitsPerSample() != streamInfo.getBitsPerSample()) {
            throw new IllegalArgumentException(String.format("Bit depth (%s) not %s",
                frameHeader.getBitsPerSample(), streamInfo.getBitsPerSample()));
        }
    }
}
