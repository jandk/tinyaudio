package be.twofold.tinyaudio.flac.metadata;

import be.twofold.tinyaudio.core.internal.IOUtils;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class CueSheet implements MetadataBlock {
    private final String mediaCatalogNumber;
    private final long leadIn;
    private final boolean cd;
    private final List<CueSheetTrack> tracks;

    public CueSheet(
        String mediaCatalogNumber,
        long leadIn,
        boolean cd,
        List<CueSheetTrack> tracks
    ) {
        this.mediaCatalogNumber = Objects.requireNonNull(mediaCatalogNumber);
        this.leadIn = leadIn;
        this.cd = cd;
        this.tracks = List.copyOf(tracks);
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

    public String getMediaCatalogNumber() {
        return mediaCatalogNumber;
    }

    public long getLeadIn() {
        return leadIn;
    }

    public boolean isCd() {
        return cd;
    }

    public List<CueSheetTrack> getTracks() {
        return tracks;
    }
}
