package be.twofold.tinyaudio.flac.metadata;

import be.twofold.tinyaudio.core.AudioFormatException;
import be.twofold.tinyaudio.core.internal.IOUtils;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class CueSheetTrack {
    private final long offset;
    private final int number;
    private final String isrc;
    private final boolean type;
    private final boolean preEmphasis;
    private final List<CueSheetIndex> indices;

    public CueSheetTrack(
        long offset,
        int number,
        String isrc,
        boolean type,
        boolean preEmphasis,
        List<CueSheetIndex> indices
    ) {
        this.offset = offset;
        this.number = number;
        this.isrc = Objects.requireNonNull(isrc);
        this.type = type;
        this.preEmphasis = preEmphasis;
        this.indices = List.copyOf(indices);
    }

    public static CueSheetTrack read(ByteBuffer buffer) throws AudioFormatException {
        long offset = buffer.getLong();
        int number = Byte.toUnsignedInt(buffer.get());
        String isrc = IOUtils.getString(buffer, 12, StandardCharsets.US_ASCII).trim();
        byte typeAndPreEmphasis = buffer.get();
        boolean type = (typeAndPreEmphasis & 0x80) != 0;
        boolean preEmphasis = (typeAndPreEmphasis & 0x40) != 0;
        CueSheet.checkPadding(buffer, 13);
        int numIndices = Byte.toUnsignedInt(buffer.get());

        List<CueSheetIndex> indices = new ArrayList<>(numIndices);
        for (int i = 0; i < numIndices; i++) {
            indices.add(CueSheetIndex.read(buffer));
        }

        return new CueSheetTrack(
            offset,
            number,
            isrc,
            type,
            preEmphasis,
            indices
        );
    }

    public long offset() {
        return offset;
    }

    public int number() {
        return number;
    }

    public String isrc() {
        return isrc;
    }

    public boolean type() {
        return type;
    }

    public boolean preEmphasis() {
        return preEmphasis;
    }

    public List<CueSheetIndex> indices() {
        return indices;
    }
}
