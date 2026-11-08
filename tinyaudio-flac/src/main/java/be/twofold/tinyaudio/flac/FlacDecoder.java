package be.twofold.tinyaudio.flac;

import be.twofold.tinyaudio.core.AudioInfo;
import be.twofold.tinyaudio.core.BlockAudioDecoder;
import be.twofold.tinyaudio.core.Packet;
import be.twofold.tinyaudio.core.PacketSource;
import be.twofold.tinyaudio.core.internal.BitReader;
import be.twofold.tinyaudio.flac.metadata.StreamInfo;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Objects;

public final class FlacDecoder extends BlockAudioDecoder {
    private static final int[][] FIXED = {
        {},
        {1},
        {2, -1},
        {3, -3, 1},
        {4, -6, 4, -1}
    };

    private final PacketSource packets;
    private final StreamInfo streamInfo;
    private final String container;

    private final int[][] samples;
    private int[] coefficients;
    private int rightShift;
    private long sampleCount;

    FlacDecoder(PacketSource packets, StreamInfo streamInfo, String container) {
        super(streamInfo.channels(), streamInfo.maxBlockSizeOrDefault());

        this.packets = Objects.requireNonNull(packets);
        this.streamInfo = Objects.requireNonNull(streamInfo);
        this.container = Objects.requireNonNull(container);
        this.samples = new int[streamInfo.channels()][streamInfo.maxBlockSizeOrDefault()];
    }

    @Override
    public AudioInfo info() {
        long total = streamInfo.totalSamples();
        return new AudioInfo(
            container, "flac", "ogg".equals(container) ? "oga" : "flac",
            streamInfo.sampleRate(),
            streamInfo.channels(),
            total == 0 ? -1 : total
        );
    }

    @Override
    protected int decodeBlock(short[][] pcm) throws IOException {
        Packet packet = packets.next();
        if (packet == null) {
            return -1;
        }

        ByteBuffer buffer = ByteBuffer.wrap(packet.getData());
        FlacFrameHeader header = FlacFrameHeader.read(buffer);
        long total = streamInfo.totalSamples();
        boolean last = total == 0 || sampleCount + header.blockSize() >= total || packet.isFinished();
        validateFrameHeader(header, streamInfo, last);
        sampleCount += header.blockSize();

        int blocksize = header.blockSize();

        BitReader bits = new BitReader(buffer.array(), buffer.position(), buffer.remaining(), ByteOrder.BIG_ENDIAN);
        for (int i = 0; i < header.numberOfChannels(); i++) {
            FlacSubFrameHeader subFrameHeader = FlacSubFrameHeader.read(bits);

            boolean side = header.channelCorrelation() != null
                && header.channelCorrelation().side() == i;
            int bitsPerSample = header.bitsPerSample() - subFrameHeader.getWastedBits() + (side ? 1 : 0);

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
        if (bits.isOverrun()) {
            throw new FlacException("Frame data truncated");
        }
        if (bits.bitsRemaining() >= 24 && !packet.isFinished()) {
            throw new FlacException("Packet contains more than one frame");
        }

        decorrelate(header.channelCorrelation(), blocksize);
        convertToShort(pcm, blocksize);
        return blocksize;
    }

    @Override
    public void close() throws IOException {
        packets.close();
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
        int shift = streamInfo.bitsPerSample() - 16;
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

    private void decodeConstant(BitReader bits, int blocksize, int bitsPerSample, int channel) throws IOException {
        int value = bits.readSigned(bitsPerSample);
        Arrays.fill(samples[channel], 0, blocksize, value);
    }

    private void decodeVerbatim(BitReader bits, int blocksize, int bitsPerSample, int channel) throws IOException {
        readInts(bits, bitsPerSample, samples[channel], 0, blocksize);
    }

    private void decodeFixed(BitReader bits, int blocksize, int bitsPerSample, int order, int channel) throws IOException {
        rightShift = 0;
        coefficients = FIXED[order];
        readInts(bits, bitsPerSample, samples[channel], 0, order);
        decodeCodedResidual(bits, blocksize, order, channel);
    }

    private void decodeLpc(BitReader bits, int blocksize, int bitsPerSample, int order, int channel) throws IOException {
        readInts(bits, bitsPerSample, samples[channel], 0, order);

        int precision = bits.read(4) + 1;
        if (precision == 16) {
            throw new FlacException("precision 16");
        }

        rightShift = bits.readSigned(5);
        coefficients = new int[order];
        readInts(bits, precision, coefficients, 0, order);
        decodeCodedResidual(bits, blocksize, order, channel);
    }

    private void decodeCodedResidual(BitReader bits, int blockSize, int order, int channel) throws IOException {
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

    private void decodePartition(BitReader bits, int codingMethod, int sampleCount, int[] target, int offset) throws IOException {
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

    private int decodeRice(BitReader bits, int riceParameter) throws IOException {
        int msb = bits.readUnary();
        int lsb = bits.read(riceParameter);
        int folded = msb << riceParameter | lsb;
        return (folded >>> 1) ^ -(folded & 1);
    }

    private void readInts(BitReader bits, int bitCount, int[] target, int offset, int count) throws IOException {
        Objects.checkFromIndexSize(offset, count, target.length);
        for (int i = offset, lim = offset + count; i < lim; i++) {
            target[i] = bits.readSigned(bitCount);
        }
    }

    private void validateFrameHeader(FlacFrameHeader header, StreamInfo streamInfo, boolean last) throws FlacException {
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
