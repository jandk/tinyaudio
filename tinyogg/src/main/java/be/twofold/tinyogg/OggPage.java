package be.twofold.tinyogg;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

public final class OggPage {
    private static final byte[] ZEROES = new byte[4];
    private final OggPageHeader header;
    private final byte[] payload;

    private OggPage(OggPageHeader header, byte[] payload) {
        this.header = Objects.requireNonNull(header);
        this.payload = Objects.requireNonNull(payload);
    }

    public static OggPage read(InputStream in) throws IOException {
        OggPageHeader header = OggPageHeader.read(in);
        byte[] payload = IOUtils.readBytes(in, header.getPayloadSize());

        OggCrc32 crc = new OggCrc32();
        crc.update(header.getRawHeader(), 0, 22);
        crc.update(ZEROES); // Zeroed crcChecksum
        crc.update(header.getRawHeader(), 26, 1);
        crc.update(header.getSegmentTable());
        crc.update(payload);

        if ((int) crc.getValue() != header.getCrcChecksum()) {
            throw new OggException("Invalid CRC checksum");
        }

        return new OggPage(header, payload);
    }

    public OggPageHeader getHeader() {
        return header;
    }

    public byte[] getPayload() {
        return payload;
    }

    @Override
    public String toString() {
        return "OggPage(" +
                "header=" + header + ", " +
                "payload=[" + payload.length + " bytes]" +
                ")";
    }
}
