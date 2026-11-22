package be.twofold.tinyaudio.flac.internal;

enum ChannelAssignment {
    INDEPENDENT(-1),
    LEFT_SIDE(1),
    SIDE_RIGHT(0),
    MID_SIDE(1),
    ;

    private final int side;

    ChannelAssignment(int side) {
        this.side = side;
    }

    int side() {
        return side;
    }
}
