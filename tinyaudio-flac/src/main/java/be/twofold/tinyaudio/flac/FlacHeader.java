package be.twofold.tinyaudio.flac;

import be.twofold.tinyaudio.core.AudioFormatException;
import be.twofold.tinyaudio.core.internal.IOUtils;
import be.twofold.tinyaudio.core.internal.Packet;
import be.twofold.tinyaudio.core.internal.PacketSource;
import be.twofold.tinyaudio.flac.metadata.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

final class FlacHeader {
    static final byte[] MAGIC = {'f', 'L', 'a', 'C'};
    static final byte[] OGG_MAGIC = {0x7F, 'F', 'L', 'A', 'C'};
    private static final int OGG_HEADER_SIZE = 13;

    private final StreamInfo streamInfo;
    private final List<MetadataBlock> blocks;

    FlacHeader(StreamInfo streamInfo, List<MetadataBlock> blocks) {
        this.streamInfo = Objects.requireNonNull(streamInfo);
        this.blocks = List.copyOf(blocks);
    }

    static FlacHeader read(InputStream in) throws IOException {
        ByteBuffer buffer = IOUtils.readBuffer(in, MAGIC.length + 4 + StreamInfo.BYTES, ByteOrder.BIG_ENDIAN);
        if (!IOUtils.matches(buffer, MAGIC)) {
            throw new AudioFormatException("Not a FLAC stream");
        }

        boolean last = buffer.get(buffer.position()) < 0;
        StreamInfo streamInfo = parseStreamInfo(buffer);

        List<MetadataBlock> blocks = new ArrayList<>();
        while (!last) {
            int header = IOUtils.readBuffer(in, 4, ByteOrder.BIG_ENDIAN).getInt();
            blocks.add(readBlock(header, in));
            last = header < 0;
        }

        return new FlacHeader(streamInfo, blocks);
    }

    static FlacHeader read(Packet first, PacketSource packets) throws IOException {
        if (first.data().length < OGG_HEADER_SIZE + 4 + StreamInfo.BYTES) {
            throw new AudioFormatException("Ogg FLAC header packet too short");
        }

        ByteBuffer buffer = ByteBuffer.wrap(first.data());
        if (!IOUtils.matches(buffer, OGG_MAGIC)) {
            throw new AudioFormatException("Invalid Ogg FLAC header");
        }
        int major = buffer.get();
        buffer.get(); // minor
        buffer.getShort(); // header packet count
        if (major != 1 || !IOUtils.matches(buffer, MAGIC)) {
            throw new AudioFormatException("Invalid Ogg FLAC header");
        }

        boolean last = buffer.get(buffer.position()) < 0;
        StreamInfo streamInfo = parseStreamInfo(buffer);

        List<MetadataBlock> blocks = new ArrayList<>();
        while (!last) {
            Packet packet = packets.next();
            if (packet == null) {
                throw new AudioFormatException("Stream ended before the last metadata block");
            }
            ByteBuffer block = ByteBuffer.wrap(packet.data());
            if (block.remaining() < 4) {
                throw new AudioFormatException("Metadata packet too short");
            }
            int header = block.getInt();
            blocks.add(parseBlock(header, block));
            last = header < 0;
        }
        return new FlacHeader(streamInfo, blocks);
    }

    private static StreamInfo parseStreamInfo(ByteBuffer buffer) throws AudioFormatException {
        int header = buffer.getInt();
        if ((header & 0x7FFF_FFFF) != StreamInfo.BYTES) {
            throw new AudioFormatException("First metadata block must be a StreamInfo block");
        }
        return (StreamInfo) parseBlock(header, buffer);
    }

    private static MetadataBlock readBlock(int header, InputStream in) throws IOException {
        int length = header & 0x00FF_FFFF;
        ByteBuffer buffer = IOUtils.readBuffer(in, length, ByteOrder.BIG_ENDIAN);
        return parseBlock(header, buffer);
    }

    private static MetadataBlock parseBlock(int header, ByteBuffer data) throws AudioFormatException {
        int code = (header >> 24) & 0x7F;
        if (code == 127) {
            throw new AudioFormatException("Forbidden metadata block type: 127");
        }
        MetadataType type = MetadataType.fromCode(code);
        int length = header & 0x00FF_FFFF;
        if (data.remaining() < length) {
            throw new AudioFormatException("Truncated metadata block");
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
                throw new AudioFormatException("Invalid stream info block", e);
            }
            // failed metadata shouldn't cause an exception
            return Unknown.read(buffer.rewind(), code);
        }
    }

    StreamInfo streamInfo() {
        return streamInfo;
    }

    List<MetadataBlock> blocks() {
        return blocks;
    }
}
