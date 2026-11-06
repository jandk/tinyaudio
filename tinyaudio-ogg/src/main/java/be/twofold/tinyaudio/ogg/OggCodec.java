package be.twofold.tinyaudio.ogg;

import be.twofold.tinyaudio.core.AudioDecoder;
import be.twofold.tinyaudio.core.Packet;
import be.twofold.tinyaudio.core.PacketSource;

import java.io.IOException;
import java.util.Arrays;

public interface OggCodec {

    String name();

    boolean identifies(byte[] packet);

    AudioDecoder open(Packet first, PacketSource packets) throws IOException;

    static boolean startsWith(byte[] packet, byte[] magic) {
        return packet.length >= magic.length
            && Arrays.equals(packet, 0, magic.length, magic, 0, magic.length);
    }
}
