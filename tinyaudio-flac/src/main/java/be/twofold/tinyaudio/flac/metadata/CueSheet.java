package be.twofold.tinyaudio.flac.metadata;

import be.twofold.tinyaudio.core.internal.IOUtils;
import be.twofold.tinyaudio.flac.FlacException;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class CueSheet implements MetadataBlock {
    private final String mediaCatalogNumber;
    private final long leadIn;
    private final boolean cdda;
    private final List<CueSheetTrack> tracks;

    public CueSheet(
        String mediaCatalogNumber,
        long leadIn,
        boolean cdda,
        List<CueSheetTrack> tracks
    ) {
        this.mediaCatalogNumber = Objects.requireNonNull(mediaCatalogNumber);
        this.leadIn = leadIn;
        this.cdda = cdda;
        this.tracks = List.copyOf(tracks);
    }

    public static CueSheet read(ByteBuffer buffer) throws FlacException {
        String mediaCatalogNumber = IOUtils.getString(buffer, 128, StandardCharsets.US_ASCII).trim();
        long leadIn = buffer.getLong();
        boolean cdda = (buffer.get() & 0x80) != 0;
        checkPadding(buffer, 258);
        int numTracks = Byte.toUnsignedInt(buffer.get());

        List<CueSheetTrack> tracks = new ArrayList<>(numTracks);
        for (int i = 0; i < numTracks; i++) {
            tracks.add(CueSheetTrack.read(buffer));
        }

        return new CueSheet(
            mediaCatalogNumber,
            leadIn,
            cdda,
            tracks
        );
    }

    static void checkPadding(ByteBuffer buffer, int length) throws FlacException {
        for (int i = 0; i < length; i++) {
            if (buffer.get() != 0) {
                throw new FlacException("Invalid padding");
            }
        }
    }

    public String mediaCatalogNumber() {
        return mediaCatalogNumber;
    }

    public long leadIn() {
        return leadIn;
    }

    public boolean cdda() {
        return cdda;
    }

    public List<CueSheetTrack> tracks() {
        return tracks;
    }
}
