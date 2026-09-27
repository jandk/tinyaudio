package be.twofold.tinyflac.metadata;

import java.io.DataInputStream;
import java.io.IOException;

public final class CueSheetIndex {
    private final long offset;
    private final byte number;

    public CueSheetIndex(
        long offset,
        byte number
    ) {
        this.offset = offset;
        this.number = number;
    }

    public static CueSheetIndex read(DataInputStream dis) throws IOException {
        long offset = dis.readLong();
        byte number = dis.readByte();
        for (int i = 0; i < 3; i++) {
            if (dis.readByte() != 0) {
                throw new IOException("Invalid cue sheet index");
            }
        }

        return new CueSheetIndex(offset, number);
    }

    public long getOffset() {
        return offset;
    }

    public byte getNumber() {
        return number;
    }
}
