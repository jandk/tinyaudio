package be.twofold.tinyaudio.flac.metadata;

public final class Padding implements MetadataBlock {
    private final int length;

    public Padding(int length) {
        this.length = length;
    }

    public int getLength() {
        return length;
    }
}
