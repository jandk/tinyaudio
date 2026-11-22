package be.twofold.tinyaudio.core.internal;

import java.io.Closeable;
import java.io.IOException;

public interface PacketSource extends Closeable {
    Packet next() throws IOException;
}
