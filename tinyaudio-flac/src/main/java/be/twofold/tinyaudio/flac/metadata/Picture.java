package be.twofold.tinyaudio.flac.metadata;

import be.twofold.tinyaudio.core.AudioFormatException;
import be.twofold.tinyaudio.core.internal.IOUtils;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
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

    public static Picture read(ByteBuffer buffer) throws AudioFormatException {
        int typeCode = buffer.getInt();
        PictureType type = PictureType.fromCode(typeCode);
        if (type == null) {
            throw new AudioFormatException("Invalid picture type code: " + typeCode);
        }

        String mimeType = IOUtils.getString(buffer, buffer.getInt(), StandardCharsets.US_ASCII);
        String description = IOUtils.getString(buffer, buffer.getInt(), StandardCharsets.UTF_8);
        int width = buffer.getInt();
        int height = buffer.getInt();
        int depth = buffer.getInt();
        int colors = buffer.getInt();
        byte[] data = IOUtils.getBytes(buffer, buffer.getInt());

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

    public PictureType type() {
        return type;
    }

    public String mimeType() {
        return mimeType;
    }

    public String description() {
        return description;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public int depth() {
        return depth;
    }

    public int colors() {
        return colors;
    }

    public byte[] data() {
        return data.clone();
    }
}
