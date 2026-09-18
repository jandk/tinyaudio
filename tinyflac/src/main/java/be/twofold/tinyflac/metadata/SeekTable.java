package be.twofold.tinyflac.metadata;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public record SeekTable(
    List<SeekPoint> points
) implements MetadataBlock {
    public static SeekTable read(DataInputStream dis) throws IOException {
        if (dis.available() % SeekPoint.BYTES != 0) {
            throw new IOException("Seek table size is not a multiple of " + SeekPoint.BYTES);
        }

        int count = dis.available() / SeekPoint.BYTES;
        List<SeekPoint> points = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            points.add(SeekPoint.read(dis));
        }

        return new SeekTable(points);
    }
}
