package be.twofold.tinyaudio.flac.internal;

import be.twofold.tinyaudio.core.internal.CRC;
import be.twofold.tinyaudio.flac.FlacException;
import be.twofold.tinyaudio.flac.metadata.StreamInfo;

import java.nio.ByteBuffer;

public final class FrameHeader {
    private final boolean variableBlockSize;
    private final int blockSize;
    private final int sampleRate;
    private final int channels;
    private final ChannelAssignment channelAssignment;
    private final int bitsPerSample;
    private final long codedNumber;

    FrameHeader(
        boolean variableBlockSize,
        int blockSize,
        int sampleRate,
        int channels,
        ChannelAssignment channelAssignment,
        int bitsPerSample,
        long codedNumber
    ) {
        this.variableBlockSize = variableBlockSize;
        this.blockSize = blockSize;
        this.sampleRate = sampleRate;
        this.channels = channels;
        this.channelAssignment = channelAssignment;
        this.bitsPerSample = bitsPerSample;
        this.codedNumber = codedNumber;
    }

    public static FrameHeader read(ByteBuffer buffer) throws FlacException {
        int position = buffer.position();
        int b0 = Byte.toUnsignedInt(buffer.get());
        int b1 = Byte.toUnsignedInt(buffer.get());
        int b2 = Byte.toUnsignedInt(buffer.get());
        int b3 = Byte.toUnsignedInt(buffer.get());

        if (b0 != 0xFF || b1 >> 1 != 0x7C) {
            throw new FlacException("Invalid sync");
        }

        boolean variableBlockSize = (b1 & 0x01) != 0;
        int channels = parseChannels(b3 >> 4);
        ChannelAssignment channelAssignment = parseChannelAssignment(b3 >> 4);
        int bitsPerSample = parseBitsPerSample((b3 >> 1) & 0x07);
        if ((b3 & 1) != 0) {
            throw new FlacException("Reserved bit must be zero");
        }

        long codedNumber = parseCodedNumber(buffer);
        int blockSize = parseBlockSize(b2 >> 4, buffer);
        int sampleRate = parseSampleRate(b2 & 0x0F, buffer);

        ByteBuffer crcBuffer = buffer.duplicate().position(position).limit(buffer.position());
        var crc = CRC.FLAC_HEADER.get();
        crc.update(crcBuffer);
        if ((byte) crc.getValue() != buffer.get()) {
            throw new FlacException("Invalid frame header crc");
        }

        return new FrameHeader(
            variableBlockSize,
            blockSize,
            sampleRate,
            channels,
            channelAssignment,
            bitsPerSample,
            codedNumber
        );
    }

    private static int parseChannels(int channelBits) throws FlacException {
        if (channelBits < 8) {
            return channelBits + 1;
        } else if (channelBits < 11) {
            return 2;
        } else throw new FlacException("Invalid channel bits: " + channelBits);
    }

    private static ChannelAssignment parseChannelAssignment(int channelBits) {
        switch (channelBits) {
            case 1:
                return ChannelAssignment.LEFT_RIGHT;
            case 8:
                return ChannelAssignment.LEFT_SIDE;
            case 9:
                return ChannelAssignment.SIDE_RIGHT;
            case 10:
                return ChannelAssignment.MID_SIDE;
            default:
                return null;
        }
    }

    private static int parseBitsPerSample(int bitsPerSampleBits) throws FlacException {
        switch (bitsPerSampleBits) {
            case 0:
                return -1;
            case 1:
                return 8;
            case 2:
                return 12;
            case 4:
                return 16;
            case 5:
                return 20;
            case 6:
                return 24;
            case 7:
                return 32;
            default:
                throw new FlacException("Invalid bit depth bits: " + bitsPerSampleBits);
        }
    }

    private static long parseCodedNumber(ByteBuffer buffer) throws FlacException {
        int b = Byte.toUnsignedInt(buffer.get());
        if (b < 0x80) {
            return b;
        }

        int extra = Integer.numberOfLeadingZeros(~(b << 24)) - 1;
        if (extra > 6) {
            throw new FlacException("Invalid UTF-8 start byte: " + b);
        }

        long codedNumber = b & (0x7F >> extra);
        long minValid = 1L << (extra * 5 + (extra == 1 ? 2 : 1));

        for (int i = 0; i < extra; i++) {
            int continuation = Byte.toUnsignedInt(buffer.get());
            if ((continuation & 0xC0) != 0x80) {
                throw new FlacException("Invalid continuation byte: " + continuation);
            }
            codedNumber = (codedNumber << 6) | (continuation & 0x3F);
        }

        if (codedNumber < minValid) {
            throw new FlacException("Non-canonical coded number: " + codedNumber);
        }
        return codedNumber;
    }

    private static int parseBlockSize(int blockSizeBits, ByteBuffer buffer) throws FlacException {
        switch (blockSizeBits) {
            case 1:
                return 192;
            case 2:
            case 3:
            case 4:
            case 5:
                return 144 << blockSizeBits;
            case 6:
                return Byte.toUnsignedInt(buffer.get()) + 1;
            case 7:
                return Short.toUnsignedInt(buffer.getShort()) + 1;
            case 8:
            case 9:
            case 10:
            case 11:
            case 12:
            case 13:
            case 14:
            case 15:
                return 1 << blockSizeBits;
            default:
                throw new FlacException("Invalid block size bits: " + blockSizeBits);
        }
    }

    private static int parseSampleRate(int sampleRateBits, ByteBuffer buffer) throws FlacException {
        switch (sampleRateBits) {
            case 0:
                return -1;
            case 1:
                return 88200;
            case 2:
                return 176400;
            case 3:
                return 192000;
            case 4:
                return 8000;
            case 5:
                return 16000;
            case 6:
                return 22050;
            case 7:
                return 24000;
            case 8:
                return 32000;
            case 9:
                return 44100;
            case 10:
                return 48000;
            case 11:
                return 96000;
            case 12:
                return Byte.toUnsignedInt(buffer.get()) * 1000;
            case 13:
                return Short.toUnsignedInt(buffer.getShort());
            case 14:
                return Short.toUnsignedInt(buffer.getShort()) * 10;
            default:
                throw new FlacException("Invalid sample rate bits: " + sampleRateBits);
        }
    }

    boolean variableBlockSize() {
        return variableBlockSize;
    }

    public int blockSize() {
        return blockSize;
    }

    public int sampleRate() {
        return sampleRate;
    }

    public int channels() {
        return channels;
    }

    ChannelAssignment channelAssignment() {
        return channelAssignment;
    }

    public int bitsPerSample() {
        return bitsPerSample;
    }

    long codedNumber() {
        return codedNumber;
    }

    public boolean matchesFirst(StreamInfo info) {
        return blockSize <= info.maxBlockSizeOrDefault()
            && channels == info.channels()
            && (sampleRate == -1 || sampleRate == info.sampleRate())
            && (bitsPerSample == -1 || bitsPerSample == info.bitsPerSample());
    }

    public boolean matches(StreamInfo info, FrameHeader first) {
        return blockSize <= info.maxBlockSizeOrDefault()
            && channels == info.channels()
            && sampleRate == first.sampleRate
            && bitsPerSample == first.bitsPerSample;
    }

}
