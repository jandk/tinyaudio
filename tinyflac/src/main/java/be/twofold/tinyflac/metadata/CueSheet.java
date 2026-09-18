package be.twofold.tinyflac.metadata;

import be.twofold.tinyflac.internal.IOUtils;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public record CueSheet(
    String mediaCatalogNumber,
    long leadIn,
    boolean isCd,
    List<CueSheetTrack> tracks
) implements MetadataBlock {
    public CueSheet {
        Objects.requireNonNull(mediaCatalogNumber);
        tracks = List.copyOf(tracks);
    }

    public static CueSheet read(DataInputStream dis) throws IOException {
        String mediaCatalogNumber = IOUtils.readString(dis, 128).trim();
        long leadIn = dis.readLong();
        byte isCdByte = dis.readByte();
        boolean isCd = (isCdByte & 0x80) != 0;

        for (int i = 0; i < 258; i++) {
            if (dis.readByte() != 0) {
                throw new IOException("Invalid cue sheet");
            }
        }

        int numTracks = dis.readUnsignedByte();
        List<CueSheetTrack> tracks = new ArrayList<>(numTracks);
        for (int i = 0; i < numTracks; i++) {
            tracks.add(CueSheetTrack.read(dis));
        }

        return new CueSheet(
            mediaCatalogNumber,
            leadIn,
            isCd,
            tracks
        );
    }
}
