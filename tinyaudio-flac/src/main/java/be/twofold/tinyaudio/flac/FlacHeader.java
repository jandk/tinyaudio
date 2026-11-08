package be.twofold.tinyaudio.flac;

import be.twofold.tinyaudio.core.Packet;
import be.twofold.tinyaudio.core.PacketSource;
import be.twofold.tinyaudio.core.internal.IOUtils;
import be.twofold.tinyaudio.flac.metadata.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

final class FlacHeader {
    static final byte[] MAGIC = new byte[]{'f', 'L', 'a', 'C'};
    static final byte[] OGG_MAGIC = {0x7F, 'F', 'L', 'A', 'C'};
    private static final int OGG_HEADER_SIZE = 13;

    private final StreamInfo streamInfo;
    private final List<MetadataBlock> blocks;

    FlacHeader(StreamInfo streamInfo, List<MetadataBlock> blocks) {
        this.streamInfo = Objects.requireNonNull(streamInfo);
        this.blocks = List.copyOf(blocks);
    }

    static FlacHeader read(InputStream in) throws IOException {
        if (!Arrays.equals(IOUtils.readBytes(in, 4), MAGIC)) {
            throw new FlacException("Not a FLAC stream");
        }

        int header = readInt(in);
        checkStreamInfoFirst(header);
        StreamInfo streamInfo = (StreamInfo) readBlock(header, in);

        List<MetadataBlock> blocks = new ArrayList<>();
        while (header >= 0) {
            header = readInt(in);
            blocks.add(readBlock(header, in));
        }

        return new FlacHeader(streamInfo, blocks);
    }

    static FlacHeader read(Packet first, PacketSource packets) throws IOException {
        ByteBuffer buffer = ByteBuffer.wrap(first.getData());
        if (buffer.remaining() < OGG_HEADER_SIZE + 4 + StreamInfo.BYTES) {
            throw new FlacException("Ogg FLAC header packet too short");
        }

        buffer.position(5); // already checked
        int major = buffer.get();
        buffer.get(); // minor
        buffer.getShort(); // header packet count
        if (major != 1 || !Arrays.equals(IOUtils.getBytes(buffer, 4), MAGIC)) {
            throw new FlacException("Invalid Ogg FLAC header");
        }

        int header = buffer.getInt();
        checkStreamInfoFirst(header);
        StreamInfo streamInfo = (StreamInfo) parseBlock(header, buffer);

        List<MetadataBlock> metadataBlocks = new ArrayList<>();
        while (header >= 0) {
            Packet packet = packets.next();
            if (packet == null) {
                throw new FlacException("Stream ended before the last metadata block");
            }
            ByteBuffer block = ByteBuffer.wrap(packet.getData());
            if (block.remaining() < 4) {
                throw new FlacException("Metadata packet too short");
            }
            header = block.getInt();
            metadataBlocks.add(parseBlock(header, block));
        }
        return new FlacHeader(streamInfo, metadataBlocks);
    }

    private static void checkStreamInfoFirst(int header) throws FlacException {
        if ((header & 0x7FFF_FFFF) != StreamInfo.BYTES) {
            throw new FlacException("First metadata block must be a StreamInfo block");
        }
    }

    private static MetadataBlock readBlock(int header, InputStream in) throws IOException {
        int length = header & 0x00FF_FFFF;
        ByteBuffer buffer = IOUtils.readBuffer(in, length, ByteOrder.BIG_ENDIAN);
        return parseBlock(header, buffer);
    }

    private static MetadataBlock parseBlock(int header, ByteBuffer data) throws FlacException {
        int code = (header >> 24) & 0x7F;
        if (code == 127) {
            throw new FlacException("Forbidden metadata block type: 127");
        }
        MetadataType type = MetadataType.fromCode(code);
        int length = header & 0x00FF_FFFF;
        if (data.remaining() < length) {
            throw new FlacException("Truncated metadata block");
        }

        ByteBuffer buffer = data.slice().limit(length);
        try {
            switch (type) {
                case STREAM_INFO:
                    return StreamInfo.read(buffer);
                case PADDING:
                    return Padding.read(buffer);
                case APPLICATION:
                    return Application.read(buffer);
                case SEEK_TABLE:
                    return SeekTable.read(buffer);
                case VORBIS_COMMENT:
                    return CommentBlock.read(buffer);
                case CUE_SHEET:
                    return CueSheet.read(buffer);
                case PICTURE:
                    return Picture.read(buffer);
                case UNDEFINED:
                    return Unknown.read(buffer, code);
                default:
                    throw new AssertionError();
            }
        } catch (BufferUnderflowException | IOException e) {
            if (type == MetadataType.STREAM_INFO) {
                throw new FlacException("Invalid stream info block", e);
            }
            // failed metadata shouldn't cause an exception
            return Unknown.read(buffer.rewind(), code);
        }
    }

    private static int readInt(InputStream in) throws IOException {
        return IOUtils.readBuffer(in, 4, ByteOrder.BIG_ENDIAN).getInt();
    }

    StreamInfo streamInfo() {
        return streamInfo;
    }

    List<MetadataBlock> blocks() {
        return blocks;
    }
}
