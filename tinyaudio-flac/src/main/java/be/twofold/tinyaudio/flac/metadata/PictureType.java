package be.twofold.tinyaudio.flac.metadata;

public enum PictureType {
    OTHER,
    FILE_ICON_STANDARD,
    FILE_ICON,
    FRONT_COVER,
    BACK_COVER,
    LEAFLET_PAGE,
    MEDIA,
    LEAD_ARTIST,
    ARTIST,
    CONDUCTOR,
    BAND,
    COMPOSER,
    LYRICIST,
    RECORDING_LOCATION,
    DURING_RECORDING,
    DURING_PERFORMANCE,
    VIDEO_SCREEN_CAPTURE,
    FISH,
    ILLUSTRATION,
    BAND_LOGOTYPE,
    PUBLISHER_LOGOTYPE,
    ;

    private static final PictureType[] VALUES = values();

    static PictureType fromCode(int code) {
        if (code < 0 || code > PUBLISHER_LOGOTYPE.ordinal()) {
            return OTHER;
        }
        return VALUES[code];
    }
}
