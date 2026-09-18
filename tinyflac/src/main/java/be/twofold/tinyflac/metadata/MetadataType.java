package be.twofold.tinyflac.metadata;

public enum MetadataType {
    STREAM_INFO,
    PADDING,
    APPLICATION,
    SEEK_TABLE,
    VORBIS_COMMENT,
    CUE_SHEET,
    PICTURE,
    UNDEFINED;

    public static MetadataType fromCode(int code) {
        return switch (code) {
            case 0 -> STREAM_INFO;
            case 1 -> PADDING;
            case 2 -> APPLICATION;
            case 3 -> SEEK_TABLE;
            case 4 -> VORBIS_COMMENT;
            case 5 -> CUE_SHEET;
            case 6 -> PICTURE;
            default -> UNDEFINED;
        };
    }
}
