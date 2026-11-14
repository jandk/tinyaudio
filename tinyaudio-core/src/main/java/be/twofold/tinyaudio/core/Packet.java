package be.twofold.tinyaudio.core;

import java.util.Objects;

public final class Packet {
    private final byte[] data;
    private final long position;
    private final boolean finished;
    private final boolean gap;

    public Packet(byte[] data, long position, boolean finished, boolean gap) {
        this.data = Objects.requireNonNull(data);
        this.position = position;
        this.finished = finished;
        this.gap = gap;
    }

    public byte[] data() {
        return data;
    }

    public long position() {
        return position;
    }

    public boolean finished() {
        return finished;
    }

    public boolean gap() {
        return gap;
    }

    @Override
    public String toString() {
        return "Packet(" +
            "data=[" + data.length + " bytes], " +
            "position=" + position + ", " +
            "finished=" + finished + ", " +
            "gap=" + gap +
            ")";
    }
}
