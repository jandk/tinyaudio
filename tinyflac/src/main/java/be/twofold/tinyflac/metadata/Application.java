package be.twofold.tinyflac.metadata;

import java.io.DataInputStream;
import java.io.IOException;

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

    public static Application read(DataInputStream dis) throws IOException {
        int id = dis.readInt();
        byte[] data = dis.readAllBytes();

        return new Application(
            id,
            data
        );
    }

    public int getId() {
        return id;
    }

    public byte[] getData() {
        return data.clone();
    }
}
