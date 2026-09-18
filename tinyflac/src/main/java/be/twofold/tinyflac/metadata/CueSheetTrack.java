package be.twofold.tinyflac.metadata;

import be.twofold.tinyflac.internal.IOUtils;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public record CueSheetTrack(
    long offset,
    byte number,
    String isrc,
    boolean type,
    boolean preEmphasis,
    List<CueSheetIndex> indices
) {
    public CueSheetTrack {
        Objects.requireNonNull(isrc);
        indices = List.copyOf(indices);
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
}
