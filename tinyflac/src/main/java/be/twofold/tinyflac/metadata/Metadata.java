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

public record Metadata(
    StreamInfo streamInfo,
    List<MetadataBlock> blocks
) {
    private static final int MAGIC = 'f' << 24 | 'L' << 16 | 'a' << 8 | 'C';

    public Metadata {
        Objects.requireNonNull(streamInfo);
        blocks = List.copyOf(blocks);
    }

    public static Metadata read(InputStream in) throws IOException {
        return read(in, true);
    }

    public static StreamInfo readStreamInfo(InputStream in) throws IOException {
        return read(in, false).streamInfo();
    }

    private static Metadata read(InputStream in, boolean full) throws IOException {
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
        blocks.add(streamInfo);

        if (full) {
            while (header >= 0) {
                header = readInt(in);
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
            in.skipNBytes(length);
            return new Padding(length);
        }

        byte[] body = IOUtils.readBytes(in, length);
        DataInputStream dis = new DataInputStream(new ByteArrayInputStream(body));
        return switch (type) {
            case STREAM_INFO -> StreamInfo.read(dis);
            case PADDING -> throw new AssertionError();
            case APPLICATION -> Application.read(dis);
            case SEEK_TABLE -> SeekTable.read(dis);
            case VORBIS_COMMENT -> VorbisComment.read(dis);
            case CUE_SHEET -> new Unknown(code, body);
            case PICTURE -> Picture.read(dis);
            case UNDEFINED -> new Unknown(code, body);
        };
    }

    public static int readInt(InputStream in) throws IOException {
        byte[] b = IOUtils.readBytes(in, 4);
        return (b[0] & 0xFF) << 24 | (b[1] & 0xFF) << 16 | (b[2] & 0xFF) << 8 | b[3] & 0xFF;
    }
}
