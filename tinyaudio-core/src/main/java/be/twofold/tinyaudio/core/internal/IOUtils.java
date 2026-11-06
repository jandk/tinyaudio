package be.twofold.tinyaudio.core.internal;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public final class IOUtils {
    private IOUtils() {
    }

    public static byte[] getBytes(ByteBuffer buffer, int length) {
        if (length < 0 || length > buffer.remaining()) {
            throw new BufferUnderflowException();
        }
        byte[] bytes = new byte[length];
        buffer.get(bytes);
        return bytes;
    }

    public static String getString(ByteBuffer buffer, int length, Charset charset) {
        return new String(getBytes(buffer, length), charset);
    }

    public static ByteBuffer readBuffer(InputStream in, int length, ByteOrder order) throws IOException {
        return ByteBuffer
            .wrap(readBytes(in, length))
            .order(order);
    }

    public static byte[] readBytes(InputStream in, int length) throws IOException {
        byte[] bytes = in.readNBytes(length);
        if (bytes.length != length) {
            throw new EOFException("Expected " + length + " bytes, but got " + bytes.length);
        }
        return bytes;
    }

    public static int readInt(InputStream in) throws IOException {
        byte[] b = readBytes(in, 4);
        return (b[0] & 0xFF) << 24 | (b[1] & 0xFF) << 16 | (b[2] & 0xFF) << 8 | b[3] & 0xFF;
    }

    public static String readString(InputStream in, int length) throws IOException {
        return new String(readBytes(in, length), StandardCharsets.UTF_8);
    }

    public static void skipBytes(InputStream in, int length) throws IOException {
        while (length > 0) {
            long ns = in.skip(length);
            if (ns > 0 && ns <= length) {
                length -= ns;
            } else if (ns == 0) {
                if (in.read() == -1) {
                    throw new EOFException();
                }
                length--;
            } else {
                throw new IOException("Unable to skip exactly");
            }
        }
    }
}
