package be.twofold.tinyaudio.flac.metadata;

import java.nio.ByteBuffer;

public final class Padding implements MetadataBlock {
    private final int length;

    private Padding(int length) {
        this.length = length;
    }

    public static Padding read(ByteBuffer buffer) {
        return new Padding(buffer.remaining());
    }

    public int length() {
        return length;
    }
}
