package be.twofold.tinyaudio.core;

public record Packet(
    byte[] data,
    long position,
    boolean finished,
    boolean gap
) {
    @Override
    public String toString() {
        return "Packet[" +
            "data=(" + data.length + " bytes), " +
            "position=" + position + ", " +
            "finished=" + finished + ", " +
            "gap=" + gap +
            "]";
    }
}
