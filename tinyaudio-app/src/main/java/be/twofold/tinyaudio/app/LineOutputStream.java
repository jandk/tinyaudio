package be.twofold.tinyaudio.app;

import javax.sound.sampled.SourceDataLine;
import java.io.OutputStream;
import java.util.Objects;

final class LineOutputStream extends OutputStream {
    private final SourceDataLine line;

    public LineOutputStream(SourceDataLine line) {
        this.line = Objects.requireNonNull(line);
    }

    @Override
    public void write(int b) {
        write(new byte[]{(byte) b}, 0, 1);
    }

    @Override
    public void write(byte[] b, int off, int len) {
        line.write(b, off, len);
    }
}
