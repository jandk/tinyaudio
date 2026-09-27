package be.twofold.tinyflac.metadata;

import be.twofold.tinyflac.FlacException;
import be.twofold.tinyflac.internal.IOUtils;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
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
        return read(in, true);
    }

    public static StreamInfo readStreamInfo(InputStream in) throws IOException {
        return read(in, false).getStreamInfo();
    }

    private static Metadata read(InputStream in, boolean full) throws IOException {
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

        if (full) {
            while (header >= 0) {
                header = IOUtils.readInt(in);
                blocks.add(readBlock(in, header));
            }
        }

        return new Metadata(streamInfo, blocks);
    }

    private static MetadataBlock readBlock(InputStream in, int header) throws IOException {
        int code = (header >> 24) & 0x7F;
        if (code == 127) {
            throw new IOException("Forbidden metadata block type: 127");
        }
        MetadataType type = MetadataType.fromCode(code);
        int length = header & 0x00FF_FFFF;

        if (type == MetadataType.PADDING) {
            IOUtils.skipBytes(in, length);
            return new Padding(length);
        }

        byte[] body = IOUtils.readBytes(in, length);
        DataInputStream dis = new DataInputStream(new ByteArrayInputStream(body));
        switch (type) {
            case STREAM_INFO:
                return StreamInfo.read(dis);
            case PADDING:
                throw new AssertionError();
            case APPLICATION:
                return Application.read(dis);
            case SEEK_TABLE:
                return SeekTable.read(dis);
            case VORBIS_COMMENT:
                return VorbisComment.read(dis);
            case CUE_SHEET:
                return new Unknown(code, body);
            case PICTURE:
                return Picture.read(dis);
            case UNDEFINED:
                return new Unknown(code, body);
            default:
                throw new IllegalArgumentException();
        }
    }

    public StreamInfo getStreamInfo() {
        return streamInfo;
    }

    public List<MetadataBlock> getBlocks() {
        return blocks;
    }
}
