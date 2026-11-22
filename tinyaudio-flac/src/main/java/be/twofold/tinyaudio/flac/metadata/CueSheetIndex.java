package be.twofold.tinyaudio.flac.metadata;

import be.twofold.tinyaudio.core.AudioFormatException;

import java.nio.ByteBuffer;

public final class CueSheetIndex {
    private final long offset;
    private final int number;

    public CueSheetIndex(
        long offset,
        int number
    ) {
        this.offset = offset;
        this.number = number;
    }

    public static CueSheetIndex read(ByteBuffer buffer) throws AudioFormatException {
        long offset = buffer.getLong();
        int number = Byte.toUnsignedInt(buffer.get());
        CueSheet.checkPadding(buffer, 3);

        return new CueSheetIndex(
            offset,
            number
        );
    }

    public long offset() {
        return offset;
    }

    public int number() {
        return number;
    }
}
