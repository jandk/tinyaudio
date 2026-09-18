package be.twofold.tinyflac;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

public final class BitInputStream implements Closeable {
    private final InputStream is;
    private long bitBuf;
    private int bitCount;
    private int read;

    public BitInputStream(InputStream is) {
        this.is = Objects.requireNonNull(is);
    }

    public int read(int count) throws IOException {
        if (count < 0 || count > 31) {
            throw new IllegalArgumentException("count < 0 or count > 31: " + count);
        }
        return (int) readLong(count);
    }

    public int readSigned(int count) throws IOException {
        int shift = 32 - count;
        int read = read(count);
        return (read << shift) >> shift;
    }

    public boolean readBool() throws IOException {
        return readLong(1) != 0;
    }

    public int readUnary() throws IOException {
        int count = 0;
        while (read(1) == 0) {
            count++;
        }
        return count;
    }

    public long readLong(int count) throws IOException {
        if (count < 0 || count > 57) {
            throw new IllegalArgumentException("count < 0 or count > 57: " + count);
        }
        if (!refill(count)) {
            throw new IOException("Unexpected end of stream");
        }
        long result = peekBits(count);
        consume(count);
        return result;
    }

    public void align() {
        consume(bitCount & 7);
    }

    public void reset() {
        bitBuf = 0;
        bitCount = 0;
    }

    private boolean refill(int count) throws IOException {
        assert count >= 0 && count <= 57;
        while (bitCount < count) {
            long read = is.read();
            if (read < 0) {
                return false;
            }
            bitBuf = bitBuf << 8 | read;
            bitCount += 8;
        }
        return true;
    }

    private long peekBits(int count) {
        assert (count <= bitCount);
        return (bitBuf >>> bitCount - count) & ((1L << count) - 1);
    }

    private void consume(int count) {
        assert (count <= bitCount);
        bitCount -= count;
        read += count;
    }

    @Override
    public void close() throws IOException {
        is.close();
    }
}
