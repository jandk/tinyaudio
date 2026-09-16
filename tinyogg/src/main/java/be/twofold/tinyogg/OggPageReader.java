package be.twofold.tinyogg;

import java.io.*;

public final class OggPageReader implements Closeable {
    private static final int MAX_PAGE_SIZE = 27 + 255 + 255 * 255;
    private static final byte[] CAPTURE_PATTERN = {'O', 'g', 'g', 'S'};

    private final InputStream in;

    public OggPageReader(InputStream in) {
        this.in = new BufferedInputStream(in, MAX_PAGE_SIZE);
    }

    public OggPage next() throws IOException {
        return atEndOfInput() ? null : OggPage.read(in);
    }

    public OggPage resync() throws IOException {
        while (true) {
            in.mark(MAX_PAGE_SIZE);
            if (matchesCapturePattern()) {
                in.reset();
                try {
                    return OggPage.read(in);
                } catch (OggException | EOFException e) {
                    in.reset();
                }
            } else {
                in.reset();
            }
            if (in.read() == -1) {
                return null;
            }
        }
    }

    private boolean matchesCapturePattern() throws IOException {
        for (byte b : CAPTURE_PATTERN) {
            if (in.read() != b) {
                return false;
            }
        }
        return true;
    }

    private boolean atEndOfInput() throws IOException {
        in.mark(1);
        boolean atEnd = in.read() == -1;
        in.reset();
        return atEnd;
    }

    @Override
    public void close() throws IOException {
        in.close();
    }
}
