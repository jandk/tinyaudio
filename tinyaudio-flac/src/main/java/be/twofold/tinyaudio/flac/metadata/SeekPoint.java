package be.twofold.tinyaudio.flac.metadata;

import java.nio.ByteBuffer;

public final class SeekPoint {
    public static final int BYTES = 18;

    private final long sampleNumber;
    private final long streamOffset;
    private final int frameSamples;

    public SeekPoint(long sampleNumber, long streamOffset, int frameSamples) {
        this.sampleNumber = sampleNumber;
        this.streamOffset = streamOffset;
        this.frameSamples = frameSamples;
    }

    public static SeekPoint read(ByteBuffer buffer) {
        long sampleNumber = buffer.getLong();
        long streamOffset = buffer.getLong();
        int frameSamples = Short.toUnsignedInt(buffer.getShort());

        return new SeekPoint(
            sampleNumber,
            streamOffset,
            frameSamples
        );
    }

    public long sampleNumber() {
        return sampleNumber;
    }

    public long streamOffset() {
        return streamOffset;
    }

    public int frameSamples() {
        return frameSamples;
    }
}
