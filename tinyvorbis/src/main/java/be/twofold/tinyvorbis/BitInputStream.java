package be.twofold.tinyvorbis;

import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteOrder;
import java.util.Objects;

public final class BitInputStream implements Closeable {
    private final InputStream in;
    private final boolean bigEndian;

    private long bitBuf;
    private int bitCount;
    private int bitsRead;

    public BitInputStream(InputStream in, ByteOrder order) {
        this.in = Objects.requireNonNull(in);
        this.bigEndian = Objects.requireNonNull(order) == ByteOrder.BIG_ENDIAN;
    }

    public int getBitsRead() {
        return bitsRead;
    }

    public int read(int count) throws IOException {
        if (count < 0 || count > 32) {
            throw new IllegalArgumentException("count must be between 0 and 32 inclusive");
        }
        return (int) readLong(count);
    }

    public boolean readBool() throws IOException {
        return read(1) != 0;
    }

    public long readLong(int count) throws IOException {
        if (count < 0 || count > 57) {
            throw new IllegalArgumentException("count must be between 0 and 57 inclusive");
        }
        refill(count);
        if (bitCount < count) {
            throw new EOFException("Expected " + count + " bits, but only have " + bitCount);
        }
        long result = peekBits(count);
        consume(count);
        return result;
    }

    private void refill(int count) throws IOException {
        assert count >= 0 && count <= 57;
        while (bitCount < count) {
            long read = in.read();
            if (read < 0) {
                return;
            }
            if (bigEndian) {
                bitBuf |= read << (56 - bitCount);
            } else {
                bitBuf |= read << bitCount;
            }
            bitCount += 8;
        }
    }

    private long peekBits(int count) {
        assert count <= bitCount;
        if (bigEndian) {
            return (bitBuf >>> 1) >> (63 - count);
        } else {
            return bitBuf & ((1L << count) - 1);
        }
    }

    private void consume(int count) {
        assert count <= bitCount;
        if (bigEndian) {
            bitBuf <<= count;
        } else {
            bitBuf >>>= count;
        }
        bitCount -= count;
        bitsRead += count;
    }

    @Override
    public void close() throws IOException {
        in.close();
    }
}
