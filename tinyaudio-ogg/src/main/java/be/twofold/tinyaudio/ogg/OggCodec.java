package be.twofold.tinyaudio.ogg;

import java.util.Arrays;

public interface OggCodec {

    String name();

    boolean identifies(byte[] packet);

    static boolean startsWith(byte[] packet, byte[] magic) {
        return packet.length >= magic.length
            && Arrays.equals(packet, 0, magic.length, magic, 0, magic.length);
    }
}
