package be.twofold.tinyaudio.ogg.internal;

import be.twofold.tinyaudio.core.AudioFormatException;

import java.io.*;

public final class OggPageReader implements Closeable {
    private final InputStream in;

    public OggPageReader(InputStream in) {
        this.in = new BufferedInputStream(in);
    }

    public OggPage next() throws IOException {
        try {
            return OggPage.read(in);
        } catch (EOFException e) {
            return null;
        }
    }

    public OggPage resync() throws IOException {
        while (true) {
            in.mark(OggPage.MAX_PAGE_SIZE);
            boolean candidate = startsWithMagic();
            in.reset();
            if (candidate) {
                try {
                    return OggPage.read(in);
                } catch (AudioFormatException | EOFException e) {
                    in.reset();
                }
            }
            if (in.read() == -1) {
                return null;
            }
        }
    }

    private boolean startsWithMagic() throws IOException {
        for (byte b : OggPage.MAGIC) {
            if (in.read() != b) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void close() throws IOException {
        in.close();
    }
}
