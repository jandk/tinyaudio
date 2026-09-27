package be.twofold.tinyaudio.flac.metadata;

import be.twofold.tinyaudio.flac.internal.IOUtils;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class CueSheetTrack {
    private final long offset;
    private final byte number;
    private final String isrc;
    private final boolean type;
    private final boolean preEmphasis;
    private final List<CueSheetIndex> indices;

    public CueSheetTrack(
        long offset,
        byte number,
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

    public static CueSheetTrack read(DataInputStream dis) throws IOException {
        long offset = dis.readLong();
        byte number = dis.readByte();
        String isrc = IOUtils.readString(dis, 12);
        byte typeAndPreEmphasis = dis.readByte();
        boolean type = (typeAndPreEmphasis & 0x80) != 0;
        boolean preEmphasis = (typeAndPreEmphasis & 0x40) != 0;
        for (int i = 0; i < 13; i++) {
            if (dis.readByte() != 0) {
                throw new IOException("Invalid cue sheet track");
            }
        }
        int numIndices = Byte.toUnsignedInt(dis.readByte());
        List<CueSheetIndex> indices = new ArrayList<>(numIndices);
        for (int i = 0; i < numIndices; i++) {
            indices.add(CueSheetIndex.read(dis));
        }
        return new CueSheetTrack(offset, number, isrc, type, preEmphasis, indices);
    }

    public long getOffset() {
        return offset;
    }

    public byte getNumber() {
        return number;
    }

    public String getIsrc() {
        return isrc;
    }

    public boolean isType() {
        return type;
    }

    public boolean isPreEmphasis() {
        return preEmphasis;
    }

    public List<CueSheetIndex> getIndices() {
        return indices;
    }
}
