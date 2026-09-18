package be.twofold.tinyflac.metadata;

public enum PictureType {
    OTHER(0),
    FILE_ICON_STANDARD(1),
    FILE_ICON(2),
    FRONT_COVER(3),
    BACK_COVER(4),
    LEAFLET_PAGE(5),
    MEDIA(6),
    LEAD_ARTIST(7),
    ARTIST(8),
    CONDUCTOR(9),
    BAND(10),
    COMPOSER(11),
    LYRICIST(12),
    RECORDING_LOCATION(13),
    DURING_RECORDING(14),
    DURING_PERFORMANCE(15),
    VIDEO_SCREEN_CAPTURE(16),
    FISH(17),
    ILLUSTRATION(18),
    BAND_LOGOTYPE(19),
    PUBLISHER_LOGOTYPE(20),
    ;

    private final int code;

    PictureType(int code) {
        this.code = code;
    }

    static PictureType fromCode(int code) {
        for (PictureType value : values()) {
            if (value.code == code) {
                return value;
            }
        }
        return null;
    }

    public int code() {
        return code;
    }
}
