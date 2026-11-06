package be.twofold.tinyaudio.flac.metadata;

import be.twofold.tinyaudio.core.internal.IOUtils;
import be.twofold.tinyaudio.flac.FlacException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class Metadata {
    private static final int MAGIC = 'f' << 24 | 'L' << 16 | 'a' << 8 | 'C';

    private final StreamInfo streamInfo;
    private final List<MetadataBlock> blocks;

    public Metadata(StreamInfo streamInfo, List<MetadataBlock> blocks) {
        this.streamInfo = Objects.requireNonNull(streamInfo);
        this.blocks = List.copyOf(blocks);
    }

    public static Metadata read(InputStream in) throws IOException {
        int magic = IOUtils.readInt(in);
        if (magic != MAGIC) {
            throw new FlacException("Not a FLAC stream");
        }

        int header = IOUtils.readInt(in);
        if ((header & 0x7FFF_FFFF) != StreamInfo.BYTES) {
            throw new FlacException("First metadata block must be a StreamInfo block");
        }
        StreamInfo streamInfo = (StreamInfo) readBlock(in, header);

        List<MetadataBlock> blocks = new ArrayList<>();
        blocks.add(streamInfo);

        while (header >= 0) {
            header = IOUtils.readInt(in);
            blocks.add(readBlock(in, header));
        }

        return new Metadata(streamInfo, blocks);
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
                    return VorbisComment.read(buffer);
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
            return Unknown.read(buffer.rewind(), code);
        }
    }

    public StreamInfo streamInfo() {
        return streamInfo;
    }

    public List<MetadataBlock> blocks() {
        return blocks;
    }
}
