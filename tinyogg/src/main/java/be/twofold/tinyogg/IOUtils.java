package be.twofold.tinyogg;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;

final class IOUtils {
    private IOUtils() {
    }

    static byte[] readBytes(InputStream in, int length) throws IOException {
        byte[] bytes = in.readNBytes(length);
        if (bytes.length != length) {
            throw new EOFException("Expected " + length + " bytes, but got " + bytes.length);
        }
        return bytes;
    }
}
