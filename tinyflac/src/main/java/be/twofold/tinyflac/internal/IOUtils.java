package be.twofold.tinyflac.internal;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public final class IOUtils {
    private IOUtils() {
    }

    public static byte[] readBytes(InputStream in, int length) throws IOException {
        byte[] bytes = in.readNBytes(length);
        if (bytes.length != length) {
            throw new EOFException("Expected " + length + " bytes, but got " + bytes.length);
        }
        return bytes;
    }

    public static String readString(InputStream in, int length) throws IOException {
        return new String(readBytes(in, length), StandardCharsets.UTF_8);
    }
}
