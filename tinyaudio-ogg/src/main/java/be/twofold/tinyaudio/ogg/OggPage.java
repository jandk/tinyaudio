package be.twofold.tinyaudio.ogg;

import be.twofold.tinyaudio.core.internal.CRC;
import be.twofold.tinyaudio.core.internal.IOUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public final class OggPage {
    private static final int FIXED_SIZE = 27;
    static final int MAX_PAGE_SIZE = FIXED_SIZE + 255 + 255 * 255;
    static final byte[] MAGIC = {'O', 'g', 'g', 'S'};

    private final byte headerTypeFlag;
    private final long granulePosition;
    private final int bitstreamSerialNumber;
    private final int pageSequenceNumber;
    private final byte[] segments;
    private final byte[] payload;

    public OggPage(
        byte headerTypeFlag,
        long granulePosition,
        int bitstreamSerialNumber,
        int pageSequenceNumber,
        byte[] segments,
        byte[] payload
    ) {
        this.headerTypeFlag = headerTypeFlag;
        this.granulePosition = granulePosition;
        this.bitstreamSerialNumber = bitstreamSerialNumber;
        this.pageSequenceNumber = pageSequenceNumber;
        this.segments = Objects.requireNonNull(segments);
        this.payload = Objects.requireNonNull(payload);
    }

    public long getGranulePosition() {
        return granulePosition;
    }

    public int getBitstreamSerialNumber() {
        return bitstreamSerialNumber;
    }

    public int getPageSequenceNumber() {
        return pageSequenceNumber;
    }

    public byte[] getPayload() {
        return payload;
    }

    public static OggPage read(InputStream in) throws IOException {
        ByteBuffer buffer = IOUtils.readBuffer(in, FIXED_SIZE, ByteOrder.LITTLE_ENDIAN);
        if (!IOUtils.matches(buffer, MAGIC)) {
            throw new OggException("Invalid capture pattern");
        }
        byte streamStructureVersion = buffer.get();
        if (streamStructureVersion != 0) {
            throw new OggException("Invalid stream structure version: " + buffer.get());
        }
        byte headerTypeFlag = buffer.get();
        long granulePosition = buffer.getLong();
        int bitstreamSerialNumber = buffer.getInt();
        int pageSequenceNumber = buffer.getInt();
        int crcChecksum = buffer.getInt();
        buffer.putInt(22, 0); // clear crc
        int segmentCount = Byte.toUnsignedInt(buffer.get());

        byte[] segments = IOUtils.readBytes(in, segmentCount);
        int payloadSize = 0;
        for (byte segment : segments) {
            payloadSize += Byte.toUnsignedInt(segment);
        }

        byte[] payload = IOUtils.readBytes(in, payloadSize);

        CRC crc = CRC.OGG_PAGE.get();
        crc.update(buffer.rewind());
        crc.update(segments);
        crc.update(payload);
        int crcValue = (int) crc.getValue();

        if (crcValue != crcChecksum) {
            throw new OggException("Invalid CRC checksum");
        }

        return new OggPage(
            headerTypeFlag,
            granulePosition,
            bitstreamSerialNumber,
            pageSequenceNumber,
            segments,
            payload
        );
    }

    public boolean isContinuation() {
        return (headerTypeFlag & 0x01) != 0;
    }

    public boolean isBeginOfStream() {
        return (headerTypeFlag & 0x02) != 0;
    }

    public boolean isEndOfStream() {
        return (headerTypeFlag & 0x04) != 0;
    }

    //
    // Segment helpers
    //

    public int segmentCount() {
        return segments.length;
    }

    public int segmentLength(int segment) {
        return Byte.toUnsignedInt(segments[segment]);
    }

    public boolean endsPacket(int segment) {
        return segmentLength(segment) < 255;
    }

    public int segmentOffset(int segment) {
        int offset = 0;
        for (int i = 0; i < segment; i++) {
            offset += segmentLength(i);
        }
        return offset;
    }

    public int packetEnd(int from) {
        int i = from;
        while (i < segments.length && !endsPacket(i)) {
            i++;
        }
        return Math.min(i + 1, segments.length);
    }

    public int packetLength(int from) {
        int length = 0;
        for (int i = from, end = packetEnd(from); i < end; i++) {
            length += segmentLength(i);
        }
        return length;
    }

    public int lastPacketEnd() {
        for (int i = segments.length - 1; i >= 0; i--) {
            if (endsPacket(i)) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public String toString() {
        String bitstreamSerialNumber = String.format("0x%08X", this.bitstreamSerialNumber);
        String segmentTable = IntStream.range(0, segmentCount())
            .mapToObj(i -> Integer.toString(segmentLength(i)))
            .collect(Collectors.joining(", ", "[", "]"));

        return "OggPage(" +
            "headerTypeFlag=" + headerTypeFlag + ", " +
            "granulePosition=" + granulePosition + ", " +
            "bitstreamSerialNumber=" + bitstreamSerialNumber + ", " +
            "pageSequenceNumber=" + pageSequenceNumber + ", " +
            "segmentTable=" + segmentTable + ", " +
            "payload=[" + payload.length + " bytes]" +
            ")";
    }
}
