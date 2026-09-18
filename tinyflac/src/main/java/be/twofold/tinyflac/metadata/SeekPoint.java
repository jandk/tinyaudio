package be.twofold.tinyflac.metadata;

import java.io.DataInputStream;
import java.io.IOException;

public record SeekPoint(
    long sampleNumber,
    long streamOffset,
    short frameSamples
) {
    public static final int BYTES = 18;

    public static SeekPoint read(DataInputStream dis) throws IOException {
        long sampleNumber = dis.readLong();
        long streamOffset = dis.readLong();
        short frameSamples = dis.readShort();

        return new SeekPoint(
            sampleNumber,
            streamOffset,
            frameSamples
        );
    }
}
