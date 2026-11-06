package be.twofold.tinyaudio.flac.metadata;

import java.io.IOException;
import java.nio.ByteBuffer;

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

    public static CueSheetIndex read(ByteBuffer buffer) throws IOException {
        long offset = buffer.getLong();
        byte number = buffer.get();
        CueSheet.checkPadding(buffer, 3);

        return new CueSheetIndex(
            offset,
            number
        );
    }

    public long offset() {
        return offset;
    }

    public byte number() {
        return number;
    }
}
