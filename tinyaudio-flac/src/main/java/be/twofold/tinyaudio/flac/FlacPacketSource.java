package be.twofold.tinyaudio.flac;

import be.twofold.tinyaudio.core.Packet;
import be.twofold.tinyaudio.core.PacketSource;
import be.twofold.tinyaudio.core.internal.CRC;
import be.twofold.tinyaudio.flac.internal.FlacFrameHeader;
import be.twofold.tinyaudio.flac.metadata.StreamInfo;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Objects;

final class FlacPacketSource implements PacketSource {
    private static final int MAX_FRAME_SIZE = 1 << 22;
    private static final int MAX_HEADER_SIZE = 16;

    private final CRC crc = CRC.FLAC_FRAME.get();
    private final InputStream in;
    private final StreamInfo streamInfo;
    private FlacFrameHeader first;
    private long position;
    private boolean finished;

    // Custom buffering
    private byte[] buffer = new byte[1 << 12];
    private int start;
    private int end;

    FlacPacketSource(InputStream in, StreamInfo streamInfo) {
        this.in = Objects.requireNonNull(in);
        this.streamInfo = Objects.requireNonNull(streamInfo);
    }

    @Override
    public Packet next() throws IOException {
        if (finished || !findSync()) {
            return null;
        }
        fill(MAX_HEADER_SIZE);
        FlacFrameHeader header = readHeader(0);
        position += header.blockSize();
        if (first == null) {
            first = header;
        }

        int size = findFrameEnd();
        byte[] data = Arrays.copyOfRange(buffer, start, start + size);
        start += size;

        long totalSamples = streamInfo.totalSamples();
        finished = !fill(1) || totalSamples > 0 && position >= totalSamples;
        return new Packet(data, position, finished, false);
    }

    private boolean findSync() throws IOException {
        while (fill(2)) {
            if (isSync(start)) {
                return true;
            }
            start++;
        }
        return false;
    }

    /**
     * We roll our own BufferedInputStream
     */
    private boolean fill(int size) throws IOException {
        if (end - start >= size) {
            return true;
        }
        if (start + size > buffer.length) {
            if (size > buffer.length) {
                buffer = Arrays.copyOf(buffer, Math.max(size, buffer.length * 2));
            }
            System.arraycopy(buffer, start, buffer, 0, end - start);
            end -= start;
            start = 0;
        }
        while (end - start < size) {
            int read = in.read(buffer, end, buffer.length - end);
            if (read < 0) {
                return false;
            }
            end += read;
        }
        return true;
    }

    private FlacFrameHeader readHeader(int offset) throws IOException {
        int from = start + offset;
        try {
            return FlacFrameHeader.read(ByteBuffer.wrap(buffer, from, end - from));
        } catch (BufferUnderflowException e) {
            throw new EOFException("Truncated frame header");
        }
    }

    private int findFrameEnd() throws IOException {
        crc.reset();
        int count = 0;
        int frameEnd = -1;
        while (count < MAX_FRAME_SIZE && fill(count + 1)) {
            crc.update(buffer[start + count]);
            count++;
            if (crc.getValue() == 0) {
                frameEnd = count;
                if (isFrameBoundary(count)) {
                    return count;
                }
            }
        }
        if (frameEnd < 0) {
            throw new FlacException("Frame end not found");
        }
        return frameEnd;
    }

    private boolean isSync(int start) {
        return buffer[start] == -1 && (buffer[start + 1] & 0xFE) == 0xF8;
    }

    private boolean isFrameBoundary(int offset) throws IOException {
        fill(offset + MAX_HEADER_SIZE);
        if (start + offset == end) {
            return true;
        }
        if (!isSync(start + offset)) {
            return false;
        }

        FlacFrameHeader header;
        try {
            header = readHeader(offset);
        } catch (FlacException | EOFException e) {
            return false;
        }
        if (!header.matches(streamInfo, first)) {
            throw new UnsupportedOperationException("Stream parameters change mid-stream");
        }
        return true;
    }

    @Override
    public void close() throws IOException {
        in.close();
    }
}
