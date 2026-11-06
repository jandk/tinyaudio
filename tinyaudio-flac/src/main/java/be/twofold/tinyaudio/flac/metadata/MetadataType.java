package be.twofold.tinyaudio.flac.metadata;

public enum MetadataType {
    STREAM_INFO,
    PADDING,
    APPLICATION,
    SEEK_TABLE,
    VORBIS_COMMENT,
    CUE_SHEET,
    PICTURE,
    UNDEFINED;

    private static final MetadataType[] VALUES = values();

    public static MetadataType fromCode(int code) {
        if (code < 0 || code >= UNDEFINED.ordinal()) {
            return UNDEFINED;
        }
        return VALUES[code];
    }
}
