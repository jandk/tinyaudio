package be.twofold.tinyogg;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public final class OggPage {
    private static final int FIXED_SIZE = 27;
    private static final int CAPTURE_PATTERN = 'O' | 'g' << 8 | 'g' << 16 | 'S' << 24;

    private final byte headerTypeFlag;
    private final long granulePosition;
    private final int bitstreamSerialNumber;
    private final int pageSequenceNumber;
    private final byte[] segments;
    private final byte[] payload;

    private OggPage(
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

    public OggPage copy() {
        return new OggPage(
            headerTypeFlag,
            granulePosition,
            bitstreamSerialNumber,
            pageSequenceNumber,
            segments.clone(),
            payload.clone()
        );
    }

    public static OggPage read(InputStream in) throws IOException {
        byte[] fixed = IOUtils.readBytes(in, FIXED_SIZE);
        if (ByteIO.getInt(fixed, 0) != CAPTURE_PATTERN) {
            throw new OggException("Invalid capture pattern");
        }
        if (fixed[4] != 0) {
            throw new OggException("Invalid stream structure version: " + Byte.toUnsignedInt(fixed[4]));
        }
        byte headerTypeFlag = fixed[5];
        long granulePosition = ByteIO.getLong(fixed, 6);
        int bitstreamSerialNumber = ByteIO.getInt(fixed, 14);
        int pageSequenceNumber = ByteIO.getInt(fixed, 18);
        int crcChecksum = ByteIO.getInt(fixed, 22);
        Arrays.fill(fixed, 22, 26, (byte) 0);
        int segmentCount = Byte.toUnsignedInt(fixed[26]);

        byte[] segments = IOUtils.readBytes(in, segmentCount);
        int payloadSize = 0;
        for (byte segment : segments) {
            payloadSize += Byte.toUnsignedInt(segment);
        }

        byte[] payload = IOUtils.readBytes(in, payloadSize);

        int crc = 0;
        crc = OggCrc32.update(crc, fixed);
        crc = OggCrc32.update(crc, segments);
        crc = OggCrc32.update(crc, payload);

        if (crc != crcChecksum) {
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

    public long granulePosition() {
        return granulePosition;
    }

    public int bitstreamSerialNumber() {
        return bitstreamSerialNumber;
    }

    public int pageSequenceNumber() {
        return pageSequenceNumber;
    }

    public byte[] payload() {
        return payload;
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
