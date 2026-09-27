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

    public static MetadataType fromCode(int code) {
        switch (code) {
            case 0:
                return STREAM_INFO;
            case 1:
                return PADDING;
            case 2:
                return APPLICATION;
            case 3:
                return SEEK_TABLE;
            case 4:
                return VORBIS_COMMENT;
            case 5:
                return CUE_SHEET;
            case 6:
                return PICTURE;
            default:
                return UNDEFINED;
        }
    }
}
