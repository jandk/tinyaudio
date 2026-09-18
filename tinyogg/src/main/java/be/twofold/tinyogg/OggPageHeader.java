package be.twofold.tinyogg;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public final class OggPageHeader {
    private static final int CAPTURE_PATTERN = 'O' | 'g' << 8 | 'g' << 16 | 'S' << 24;

    private final byte headerTypeFlag;
    private final long granulePosition;
    private final int bitstreamSerialNumber;
    private final int pageSequenceNumber;
    private final int crcChecksum;
    private final byte[] segmentTable;
    private final byte[] rawHeader;

    private OggPageHeader(
        byte headerTypeFlag,
        long granulePosition,
        int bitstreamSerialNumber,
        int pageSequenceNumber,
        int crcChecksum,
        byte[] segmentTable,
        byte[] rawHeader
    ) {
        this.rawHeader = Objects.requireNonNull(rawHeader);
        this.headerTypeFlag = headerTypeFlag;
        this.granulePosition = granulePosition;
        this.bitstreamSerialNumber = bitstreamSerialNumber;
        this.pageSequenceNumber = pageSequenceNumber;
        this.crcChecksum = crcChecksum;
        this.segmentTable = Objects.requireNonNull(segmentTable);
    }

    public static OggPageHeader read(InputStream in) throws IOException {
        byte[] rawHeader = IOUtils.readBytes(in, 27);
        ByteBuffer buffer = ByteBuffer
            .wrap(rawHeader)
            .order(ByteOrder.LITTLE_ENDIAN);

        if (buffer.getInt() != CAPTURE_PATTERN) {
            throw new OggException("Invalid capture pattern");
        }
        if (buffer.get() != 0) {
            throw new OggException("Invalid stream structure version");
        }
        byte headerTypeFlag = buffer.get();
        long granulePosition = buffer.getLong();
        int bitstreamSerialNumber = buffer.getInt();
        int pageSequenceNumber = buffer.getInt();
        int crcChecksum = buffer.getInt();
        int numberPageSegments = Byte.toUnsignedInt(buffer.get());
        byte[] segmentTable = IOUtils.readBytes(in, numberPageSegments);

        return new OggPageHeader(
            headerTypeFlag,
            granulePosition,
            bitstreamSerialNumber,
            pageSequenceNumber,
            crcChecksum,
            segmentTable,
            rawHeader
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

    public long getGranulePosition() {
        return granulePosition;
    }

    public int getBitstreamSerialNumber() {
        return bitstreamSerialNumber;
    }

    public int getPageSequenceNumber() {
        return pageSequenceNumber;
    }

    public int getCrcChecksum() {
        return crcChecksum;
    }

    public int getSegmentCount() {
        return segmentTable.length;
    }

    public int getSegment(int index) {
        return Byte.toUnsignedInt(segmentTable[index]);
    }

    public int getHeaderSize() {
        return 27 + getSegmentCount();
    }

    public int getPayloadSize() {
        int payloadSize = 0;
        for (int i = 0; i < getSegmentCount(); i++) {
            payloadSize += getSegment(i);
        }
        return payloadSize;
    }

    byte[] getSegmentTable() {
        return segmentTable;
    }

    byte[] getRawHeader() {
        return rawHeader;
    }

    @Override
    public String toString() {
        String bitstreamSerialNumber = String.format("0x%08X", this.bitstreamSerialNumber);
        String crcChecksum = String.format("0x%08X", this.crcChecksum);
        String segmentTable = IntStream.range(0, getSegmentCount())
            .mapToObj(i -> Integer.toString(getSegment(i)))
            .collect(Collectors.joining(", ", "[", "]"));

        return "OggPageHeader(" +
            "headerTypeFlag=" + headerTypeFlag + ", " +
            "granulePosition=" + Long.toUnsignedString(granulePosition) + ", " +
            "bitstreamSerialNumber=" + bitstreamSerialNumber + ", " +
            "pageSequenceNumber=" + pageSequenceNumber + ", " +
            "crcChecksum=" + crcChecksum + ", " +
            "segmentTable=" + segmentTable +
            ")";
    }
}
