package be.twofold.tinyflac;

import be.twofold.tinyaudio.core.CRC;
import be.twofold.tinyflac.metadata.StreamInfo;

import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Objects;
import java.util.zip.CheckedInputStream;

public class FlacDecoder extends InputStream {
    private static final VarHandle VH_SHORT = MethodHandles
        .byteArrayViewVarHandle(short[].class, ByteOrder.LITTLE_ENDIAN);

    private static final int[][] FIXED = {
        {},
        {1},
        {2, -1},
        {3, -3, 1},
        {4, -6, 4, -1}
    };

    private final StreamInfo streamInfo;

    private final InputStream in;
    private final byte[] oneByte = new byte[1];

    private final byte[] pcmBuffer;
    private int pcmOffset;
    private int pcmLength;

    private final int[][] samples;
    private int[] coefficients;
    private int rightShift;
    private long sampleCount;

    public FlacDecoder(InputStream in, StreamInfo streamInfo) {
        this.in = Objects.requireNonNull(in);
        this.streamInfo = Objects.requireNonNull(streamInfo);

        int bytesPerSampleFrame = streamInfo.getChannels() * ((streamInfo.getBitsPerSample() + 7) / 8);
        int maximumBlockSize = streamInfo.getMaxBlockSize();
        if (maximumBlockSize == 0) {
            maximumBlockSize = 65535;
        }

        this.pcmBuffer = new byte[bytesPerSampleFrame * maximumBlockSize];
        this.samples = new int[streamInfo.getChannels()][maximumBlockSize];
    }

    @Override
    public int read() throws IOException {
        int read = read(oneByte, 0, 1);
        if (read == 1) {
            return oneByte[0] & 0xFF;
        }
        return -1;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        Objects.checkFromIndexSize(off, len, b.length);

        int totalBytesRead = 0;
        while (totalBytesRead < len) {
            if (pcmOffset >= pcmLength) {
                if (totalBytesRead > 0) {
                    break;
                }
                if (!decodeNextFrame()) {
                    return -1;
                }
            }

            int bytesToCopy = Math.min(pcmLength - pcmOffset, len - totalBytesRead);
            System.arraycopy(pcmBuffer, pcmOffset, b, off + totalBytesRead, bytesToCopy);
            pcmOffset += bytesToCopy;
            totalBytesRead += bytesToCopy;
        }

        return totalBytesRead;
    }

    @Override
    public int available() {
        return pcmLength - pcmOffset;
    }

    @Override
    public void close() throws IOException {
        in.close();
    }

    private boolean decodeNextFrame() throws IOException {
        assert pcmOffset == pcmLength;

        CheckedInputStream checked = new CheckedInputStream(in, CRC.FLAC_FRAME.get());

        FlacFrameHeader frameHeader = FlacFrameHeader.read(checked);
        if (frameHeader == null) {
            return false;
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

            sampleCount += blocksize;
        }

        short actualCrc = (short) checked.getChecksum().getValue();
        short expectedCrc = (short) (checked.read() << 8 | checked.read());
        if (actualCrc != expectedCrc) {
            throw new IOException("CRC mismatch");
        }

        decorrelate(frameHeader.getChannelCorrelation(), blocksize);
        convertToPcm(blocksize);

        pcmLength = frameHeader.getBytesPerFrame();
        pcmOffset = 0;
        return true;
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

    private void convertToPcm(int blocksize) {
        int bytesPerSample = (streamInfo.getBitsPerSample() + 7) / 8;
        if (bytesPerSample == 1) {
            for (int i = 0, o = 0; i < blocksize; i++) {
                for (int[] sample : samples) {
                    pcmBuffer[o] = (byte) sample[i];
                    o++;
                }
            }
        } else if (bytesPerSample == 2) {
            for (int i = 0, o = 0; i < blocksize; i++) {
                for (int[] sample : samples) {
                    pcmBuffer[o/**/] = (byte) sample[i];
                    pcmBuffer[o + 1] = (byte) (sample[i] >> 8);
                    o += 2;
                }
            }
        } else if (bytesPerSample == 3) {
            for (int i = 0, o = 0; i < blocksize; i++) {
                for (int[] sample : samples) {
                    pcmBuffer[o/**/] = (byte) sample[i];
                    pcmBuffer[o + 1] = (byte) (sample[i] >> 8);
                    pcmBuffer[o + 2] = (byte) (sample[i] >> 16);
                    o += 3;
                }
            }
        } else if (bytesPerSample == 4) {
            for (int i = 0, o = 0; i < blocksize; i++) {
                for (int[] sample : samples) {
                    pcmBuffer[o/**/] = (byte) sample[i];
                    pcmBuffer[o + 1] = (byte) (sample[i] >> 8);
                    pcmBuffer[o + 2] = (byte) (sample[i] >> 16);
                    pcmBuffer[o + 3] = (byte) (sample[i] >> 24);
                    o += 4;
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
