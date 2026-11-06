package be.twofold.tinyaudio.core.internal;

import java.util.Objects;
import java.util.function.Supplier;
import java.util.zip.Checksum;

public final class CRC implements Checksum {
    public static final Supplier<CRC> FLAC_HEADER = () -> new CRC(8, 0x07);
    public static final Supplier<CRC> FLAC_FRAME = () -> new CRC(16, 0x8005);
    public static final Supplier<CRC> OGG_PAGE = () -> new CRC(32, 0x04C1_1DB7);

    private final int[] table;
    private final int width;

    private int crc;

    public CRC(int width, int poly) {
        if (width < 8 || width > 32) {
            throw new IllegalArgumentException("width must be between 8 and 32");
        }
        if (Integer.toUnsignedLong(poly) >= (1L << width)) {
            throw new IllegalArgumentException("poly must be less than 2^width");
        }
        this.width = width;
        this.table = generateTable(width, poly);
    }

    private static int[] generateTable(int width, int poly) {
        poly = poly << (Integer.SIZE - width);

        int[] table = new int[256];
        for (int i = 0; i < table.length; i++) {
            int crc = i << 24;
            for (int j = 0; j < 8; j++) {
                crc = (crc << 1) ^ (((crc >>> Integer.SIZE - 1) & 1) * poly);
            }
            table[i] = crc;
        }
        return table;
    }

    @Override
    public void update(int b) {
        crc = crc(crc, b);
    }

    @Override
    public void update(byte[] b, int off, int len) {
        Objects.checkFromIndexSize(off, len, b.length);
        int crc = this.crc;
        for (int i = off, lim = off + len; i < lim; i++) {
            crc = crc(crc, b[i]);
        }
        this.crc = crc;
    }

    private int crc(int crc, int b) {
        return table[((crc >>> 24) ^ b) & 0xFF] ^ (crc << 8);
    }

    @Override
    public long getValue() {
        int crc = this.crc >>> (Integer.SIZE - width);
        return Integer.toUnsignedLong(crc);
    }

    @Override
    public void reset() {
        crc = 0;
    }
}
