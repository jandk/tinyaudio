package be.twofold.tinyaudio.flac.metadata;

import be.twofold.tinyaudio.core.internal.IOUtils;

import java.nio.ByteBuffer;
import java.util.Objects;

public final class Unknown implements MetadataBlock {
    private final int type;
    private final byte[] data;

    public Unknown(
        int type,
        byte[] data
    ) {
        this.type = type;
        this.data = Objects.requireNonNull(data);
    }

    public static Unknown read(ByteBuffer buffer, int type) {
        byte[] data = IOUtils.getBytes(buffer, buffer.remaining());
        return new Unknown(type, data);
    }

    public int type() {
        return type;
    }

    public byte[] data() {
        return data;
    }
}
