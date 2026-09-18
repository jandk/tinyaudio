package be.twofold.tinyflac.metadata;

public record Unknown(
    int type,
    byte[] data
) implements MetadataBlock {
    public Unknown {
        data = data.clone();
    }

    @Override
    public byte[] data() {
        return data.clone();
    }
}
