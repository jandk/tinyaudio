package be.twofold.tinyaudio.flac;

import be.twofold.tinyaudio.core.internal.IOUtils;
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
    private static final int MAGIC = 'f' << 24 | 'L' << 16 | 'a' << 8 | 'C';

    private final StreamInfo streamInfo;
    private final List<MetadataBlock> blocks;

    FlacHeader(StreamInfo streamInfo, List<MetadataBlock> blocks) {
        this.streamInfo = Objects.requireNonNull(streamInfo);
        this.blocks = List.copyOf(blocks);
    }

    static FlacHeader read(InputStream in) throws IOException {
        int magic = readInt(in);
        if (magic != MAGIC) {
            throw new FlacException("Not a FLAC stream");
        }

        int header = readInt(in);
        if ((header & 0x7FFF_FFFF) != StreamInfo.BYTES) {
            throw new FlacException("First metadata block must be a StreamInfo block");
        }
        StreamInfo streamInfo = (StreamInfo) readBlock(in, header);

        List<MetadataBlock> blocks = new ArrayList<>();
        while (header >= 0) {
            header = readInt(in);
            blocks.add(readBlock(in, header));
        }

        return new FlacHeader(streamInfo, blocks);
    }

    private static MetadataBlock readBlock(InputStream in, int header) throws IOException {
        int code = (header >> 24) & 0x7F;
        if (code == 127) {
            throw new FlacException("Forbidden metadata block type: 127");
        }
        MetadataType type = MetadataType.fromCode(code);
        int length = header & 0x00FF_FFFF;

        ByteBuffer buffer = IOUtils.readBuffer(in, length, ByteOrder.BIG_ENDIAN);
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
