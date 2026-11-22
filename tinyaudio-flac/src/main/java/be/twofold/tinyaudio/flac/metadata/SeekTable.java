package be.twofold.tinyaudio.flac.metadata;

import be.twofold.tinyaudio.core.AudioFormatException;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public final class SeekTable implements MetadataBlock {
    private final List<SeekPoint> points;

    public SeekTable(List<SeekPoint> points) {
        this.points = List.copyOf(points);
    }

    public static SeekTable read(ByteBuffer buffer) throws AudioFormatException {
        if (buffer.remaining() % SeekPoint.BYTES != 0) {
            throw new AudioFormatException("Seek table size is not a multiple of " + SeekPoint.BYTES);
        }

        int count = buffer.remaining() / SeekPoint.BYTES;
        List<SeekPoint> points = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            points.add(SeekPoint.read(buffer));
        }

        return new SeekTable(points);
    }

    public List<SeekPoint> points() {
        return points;
    }
}
