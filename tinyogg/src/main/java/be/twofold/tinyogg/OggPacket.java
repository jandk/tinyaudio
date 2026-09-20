package be.twofold.tinyogg;

import java.util.Arrays;

public final class OggPacket {
    private byte[] packet = new byte[1024];
    private int length;
    private long granulePosition;
    private long packetNumber;
    private boolean beginOfStream;
    private boolean endOfStream;

    public OggPacket() {
    }

    public OggPacket(
        byte[] packet,
        long granulePosition,
        long packetNumber,
        boolean beginOfStream,
        boolean endOfStream
    ) {
        this.packet = packet;
        this.length = packet.length;
        this.granulePosition = granulePosition;
        this.packetNumber = packetNumber;
        this.beginOfStream = beginOfStream;
        this.endOfStream = endOfStream;
    }

    OggPacket copy() {
        return new OggPacket(
            Arrays.copyOf(packet, length),
            granulePosition,
            packetNumber,
            beginOfStream,
            endOfStream
        );
    }

    public byte[] packet() {
        return packet;
    }

    public int length() {
        return length;
    }

    public long granulePosition() {
        return granulePosition;
    }

    public long packetNumber() {
        return packetNumber;
    }

    public boolean isBeginOfStream() {
        return beginOfStream;
    }

    public boolean isEndOfStream() {
        return endOfStream;
    }

    void reset() {
        length = 0;
    }

    void append(byte[] src, int offset, int count) {
        int required = length + count;
        if (required > packet.length) {
            packet = Arrays.copyOf(packet, 1 << (32 - Integer.numberOfLeadingZeros(required - 1)));
        }
        System.arraycopy(src, offset, packet, length, count);
        length += count;
    }

    void complete(long granulePosition, long packetNumber, boolean beginOfStream, boolean endOfStream) {
        this.granulePosition = granulePosition;
        this.packetNumber = packetNumber;
        this.beginOfStream = beginOfStream;
        this.endOfStream = endOfStream;
    }

    @Override
    public String toString() {
        return "OggPacket(" +
            "length=" + length + ", " +
            "granulePosition=" + granulePosition + ", " +
            "packetNumber=" + packetNumber + ", " +
            "beginOfStream=" + beginOfStream + ", " +
            "endOfStream=" + endOfStream +
            ")";
    }
}
