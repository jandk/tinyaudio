package be.twofold.tinyflac.metadata;

import be.twofold.tinyflac.internal.IOUtils;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.Objects;

public record Picture(
    PictureType type,
    String mimeType,
    String description,
    int width,
    int height,
    int depth,
    int colors,
    byte[] data
) implements MetadataBlock {
    public Picture {
        Objects.requireNonNull(type);
        Objects.requireNonNull(mimeType);
        Objects.requireNonNull(description);
        data = data.clone();
    }

    public static Picture read(DataInputStream dis) throws IOException {
        int typeCode = dis.readInt();
        PictureType type = PictureType.fromCode(typeCode);
        if (type == null) {
            throw new IOException("Invalid picture type code: " + typeCode);
        }

        String mimeType = IOUtils.readString(dis, dis.readInt());
        String description = IOUtils.readString(dis, dis.readInt());
        int width = dis.readInt();
        int height = dis.readInt();
        int depth = dis.readInt();
        int colors = dis.readInt();
        byte[] data = IOUtils.readBytes(dis, dis.readInt());

        return new Picture(
            type,
            mimeType,
            description,
            width,
            height,
            depth,
            colors,
            data
        );
    }

    @Override
    public byte[] data() {
        return data.clone();
    }
}
