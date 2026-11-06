package be.twofold.tinyaudio.core;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Optional;

public interface AudioDecoderProvider {

    Optional<AudioDecoder> open(byte[] head, InputStream in) throws IOException;

    static boolean startsWith(byte[] data, byte[] magic) {
        return data.length >= magic.length
            && Arrays.equals(data, 0, magic.length, magic, 0, magic.length);
    }

}
