package be.twofold.tinyflac.metadata;

import java.io.DataInputStream;
import java.io.IOException;

public final class SeekPoint {
    public static final int BYTES = 18;

    private final long sampleNumber;
    private final long streamOffset;
    private final short frameSamples;

    public SeekPoint(long sampleNumber, long streamOffset, short frameSamples) {
        this.sampleNumber = sampleNumber;
        this.streamOffset = streamOffset;
        this.frameSamples = frameSamples;
    }

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

    public long getSampleNumber() {
        return sampleNumber;
    }

    public long getStreamOffset() {
        return streamOffset;
    }

    public short getFrameSamples() {
        return frameSamples;
    }
}
