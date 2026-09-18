package be.twofold.tinyflac.metadata;

import java.io.DataInputStream;
import java.io.IOException;

public record Application(
    int id,
    byte[] data
) implements MetadataBlock {
    public Application {
        data = data.clone();
    }

    public static Application read(DataInputStream dis) throws IOException {
        int id = dis.readInt();
        byte[] data = dis.readAllBytes();

        return new Application(
            id,
            data
        );
    }

    @Override
    public byte[] data() {
        return data.clone();
    }
}
