package be.twofold.tinyaudio.core;

import java.io.Closeable;
import java.io.IOException;

public interface AudioDecoder extends Closeable {
    AudioInfo info();

    /**
     * Reads planar 16-bit frames. Returns the frame count, or -1 at EOF.
     */
    int read(short[][] pcm, int off, int len) throws IOException;
}
