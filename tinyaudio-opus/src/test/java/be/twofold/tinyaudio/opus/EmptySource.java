package be.twofold.tinyaudio.opus;

import be.twofold.tinyaudio.core.Packet;
import be.twofold.tinyaudio.core.PacketSource;

import java.io.IOException;

final class EmptySource implements PacketSource {
    @Override
    public Packet next() throws IOException {
        return null;
    }

    @Override
    public void close() throws IOException {
    }
}
