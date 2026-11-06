package be.twofold.tinyaudio.core.internal;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;

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
}
