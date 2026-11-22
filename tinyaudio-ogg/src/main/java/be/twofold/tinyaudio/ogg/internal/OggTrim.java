package be.twofold.tinyaudio.ogg.internal;

import be.twofold.tinyaudio.core.internal.Packet;

public final class OggTrim {
    private long decoded;
    private boolean synced;
    private int start;
    private int end;

    public void update(Packet packet, int frames) {
        decoded += frames;
        start = 0;
        end = frames;
        long position = packet.position();
        if (position == -1) {
            return;
        }
        int extra = (int) Math.max(0, Math.min(decoded - position, frames));
        decoded = position;
        if (packet.finished()) {
            end = frames - extra;
        } else if (!synced) {
            start = extra;
        }
        synced = true;
    }

    public int start() {
        return start;
    }

    public int count() {
        return end - start;
    }
}
