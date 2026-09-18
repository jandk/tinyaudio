package be.twofold.tinyflac.metadata;

public record Padding(
    int length
) implements MetadataBlock {
}
