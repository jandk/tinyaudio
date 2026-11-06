package be.twofold.tinyaudio.flac.metadata;

import be.twofold.tinyaudio.core.internal.IOUtils;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.Objects;

public final class Picture implements MetadataBlock {
    private final PictureType type;
    private final String mimeType;
    private final String description;
    private final int width;
    private final int height;
    private final int depth;
    private final int colors;
    private final byte[] data;

    public Picture(
        PictureType type,
        String mimeType,
        String description,
        int width,
        int height,
        int depth,
        int colors,
        byte[] data
    ) {
        this.type = Objects.requireNonNull(type);
        this.mimeType = Objects.requireNonNull(mimeType);
        this.description = Objects.requireNonNull(description);
        this.width = width;
        this.height = height;
        this.depth = depth;
        this.colors = colors;
        this.data = data.clone();
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

    public PictureType getType() {
        return type;
    }

    public String getMimeType() {
        return mimeType;
    }

    public String getDescription() {
        return description;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public int getDepth() {
        return depth;
    }

    public int getColors() {
        return colors;
    }

    public byte[] getData() {
        return data.clone();
    }
}
