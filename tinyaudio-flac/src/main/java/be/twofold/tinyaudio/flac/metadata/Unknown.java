package be.twofold.tinyaudio.flac.metadata;

public final class Unknown implements MetadataBlock {
    private final int type;
    private final byte[] data;

    public Unknown(
        int type,
        byte[] data
    ) {
        this.type = type;
        this.data = data.clone();
    }

    public int getType() {
        return type;
    }

    public byte[] getData() {
        return data.clone();
    }
}
