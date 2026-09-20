package be.twofold.tinyflac;

import be.twofold.tinyaudio.core.CRC;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.CheckedInputStream;

public record FlacFrameHeader(
    boolean variableBlockSize,
    int blockSize,
    int sampleRate,
    int numberOfChannels,
    ChannelType channelCorrelation,
    int bitsPerSample,
    long codedNumber
) {
    public static FlacFrameHeader read(InputStream is) throws IOException {
        CheckedInputStream checked = new CheckedInputStream(is, CRC.FLAC_HEADER.get());
        DataInputStream dis = new DataInputStream(checked);

        int b0 = dis.read();
        if (b0 != 0xFF) {
            return null;
        }

        int b1 = dis.readUnsignedByte();
        if (b1 >> 1 != 0x7C) {
            return null;
        }
        int b2 = dis.readUnsignedByte();
        int b3 = dis.readUnsignedByte();

        boolean variableBlockSize = (b1 & 0x01) != 0;
        int numberOfChannels = numberOfChannels(b3 >> 4);
        ChannelType channelCorrelation = channelCorrelation(b3 >> 4);
        int bitsPerSample = bitsPerSample((b3 >> 1) & 0x07);
        if ((b3 & 1) != 0) {
            throw new IOException("Reserved bit must be zero");
        }

        long codedNumber = codedNumber(dis);
        int blockSize = blockSize(b2 >> 4, dis);
        int sampleRate = sampleRate(b2 & 0x0F, dis);

        byte crc = (byte) checked.getChecksum().getValue();
        if (crc != dis.readByte()) {
            throw new IOException("Invalid audio frame crc");
        }

        return new FlacFrameHeader(
            variableBlockSize,
            blockSize,
            sampleRate,
            numberOfChannels,
            channelCorrelation,
            bitsPerSample,
            codedNumber
        );
    }

    private static int numberOfChannels(int channelBits) throws IOException {
        if (channelBits < 8) {
            return channelBits + 1;
        } else if (channelBits < 11) {
            return 2;
        } else throw new IOException("Invalid channel bits: " + channelBits);
    }

    private static ChannelType channelCorrelation(int channelBits) throws IOException {
        return switch (channelBits) {
            case 1 -> ChannelType.LEFT_RIGHT;
            case 8 -> ChannelType.LEFT_SIDE;
            case 9 -> ChannelType.SIDE_RIGHT;
            case 10 -> ChannelType.MID_SIDE;
            default -> null;
        };
    }

    private static int bitsPerSample(int bitsPerSampleBits) throws IOException {
        return switch (bitsPerSampleBits) {
            case 0 -> -1;
            case 1 -> 8;
            case 2 -> 12;
            case 4 -> 16;
            case 5 -> 20;
            case 6 -> 24;
            case 7 -> 32;
            default -> throw new IOException("Invalid bit depth bits: " + bitsPerSampleBits);
        };
    }

    private static long codedNumber(DataInputStream dis) throws IOException {
        int b = dis.readUnsignedByte();
        if (b < 0x80) {
            return b;
        }

        int extra = Integer.numberOfLeadingZeros(~(b << 24)) - 1;
        if (extra > 6) {
            throw new IOException("Invalid UTF-8 start byte: " + b);
        }

        long codedNumber = b & (0x7F >> extra);
        long minValid = 1L << (extra * 5 + (extra == 1 ? 2 : 1));

        for (int i = 0; i < extra; i++) {
            int continuation = dis.readUnsignedByte();
            if ((continuation & 0xC0) != 0x80) {
                throw new IOException("Invalid continuation byte: " + b);
            }
            codedNumber = (codedNumber << 6) | (continuation & 0x3F);
        }

        if (codedNumber < minValid) {
            throw new IOException("Non-canonical coded number: " + codedNumber);
        }
        return codedNumber;
    }

    private static int blockSize(int blockSizeBits, DataInputStream dis) throws IOException {
        return switch (blockSizeBits) {
            case 1 -> 192;
            case 2, 3, 4, 5 -> 144 << blockSizeBits;
            case 6 -> dis.readUnsignedByte() + 1;
            case 7 -> dis.readUnsignedShort() + 1;
            case 8, 9, 10, 11, 12, 13, 14, 15 -> 1 << blockSizeBits;
            default -> throw new IOException("Invalid block size bits: " + blockSizeBits);
        };
    }

    private static int sampleRate(int sampleRateBits, DataInputStream dis) throws IOException {
        return switch (sampleRateBits) {
            case 0 -> -1;
            case 1 -> 88200;
            case 2 -> 176400;
            case 3 -> 192000;
            case 4 -> 8000;
            case 5 -> 16000;
            case 6 -> 22050;
            case 7 -> 24000;
            case 8 -> 32000;
            case 9 -> 44100;
            case 10 -> 48000;
            case 11 -> 96000;
            case 12 -> dis.readUnsignedByte() * 1000;
            case 13 -> dis.readUnsignedShort();
            case 14 -> dis.readUnsignedShort() * 10;
            default -> throw new IOException("Invalid sample rate bits: " + sampleRateBits);
        };
    }

    public int getBytesPerFrame() {
        int bytesPerSample = (bitsPerSample + 7) / 8 * numberOfChannels;
        return blockSize * bytesPerSample;
    }

    public enum ChannelType {
        LEFT_RIGHT(-1),
        LEFT_SIDE(1),
        SIDE_RIGHT(0),
        MID_SIDE(1),
        ;

        private final int side;

        ChannelType(int side) {
            this.side = side;
        }

        public int getSide() {
            return side;
        }
    }
}
