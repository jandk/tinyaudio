package be.twofold.tinyaudio.flac.metadata;

import be.twofold.tinyaudio.core.internal.IOUtils;

import java.nio.ByteBuffer;

public final class Application implements MetadataBlock {
    private final int id;
    private final byte[] data;

    public Application(
        int id,
        byte[] data
    ) {
        this.id = id;
        this.data = data.clone();
    }

    public static Application read(ByteBuffer buffer) {
        int id = buffer.getInt();
        byte[] data = IOUtils.getBytes(buffer, buffer.remaining());

        return new Application(
            id,
            data
        );
    }

    public int id() {
        return id;
    }

    public byte[] data() {
        return data.clone();
    }
}
