package be.twofold.tinyaudio.core;

import java.io.IOException;

public interface PacketSource {
    Packet next() throws IOException;
}
