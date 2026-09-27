package be.twofold.tinyaudio.flac.spi;

import be.twofold.tinyaudio.flac.metadata.StreamInfo;

import javax.sound.sampled.*;
import javax.sound.sampled.spi.AudioFileReader;
import java.io.*;
import java.net.URL;

public abstract class AbstractAudioFileReader extends AudioFileReader {
    private static final int LIMIT = StreamInfo.BYTES + 4;

    protected abstract AudioFileFormat readAudioFileFormat(InputStream is, boolean full)
        throws UnsupportedAudioFileException, IOException;

    @Override
    public final AudioFileFormat getAudioFileFormat(InputStream stream)
        throws UnsupportedAudioFileException, IOException {
        stream = buffered(stream);
        stream.mark(LIMIT);
        try {
            return readAudioFileFormat(stream, false);
        } finally {
            stream.reset();
        }
    }

    @Override
    public final AudioFileFormat getAudioFileFormat(URL url)
        throws UnsupportedAudioFileException, IOException {
        try (InputStream is = url.openStream()) {
            return getAudioFileFormat(is);
        }
    }

    @Override
    public final AudioFileFormat getAudioFileFormat(File file)
        throws UnsupportedAudioFileException, IOException {
        try (InputStream is = new FileInputStream(file)) {
            return getAudioFileFormat(is);
        }
    }

    @Override
    public final AudioInputStream getAudioInputStream(InputStream stream)
        throws UnsupportedAudioFileException, IOException {
        stream = buffered(stream);

        AudioFileFormat fileFormat = readAudioFileFormat(stream, true);
        AudioFormat format = fileFormat.getFormat();
        long length = format.getFrameSize() == AudioSystem.NOT_SPECIFIED
            ? AudioSystem.NOT_SPECIFIED
            : fileFormat.getFrameLength();
        return new AudioInputStream(stream, format, length);
    }

    @Override
    public final AudioInputStream getAudioInputStream(URL url)
        throws UnsupportedAudioFileException, IOException {
        InputStream is = url.openStream();
        try {
            return getAudioInputStream(is);
        } catch (Throwable e) {
            closeQuietly(is, e);
            throw e;
        }
    }

    @Override
    public final AudioInputStream getAudioInputStream(File file)
        throws UnsupportedAudioFileException, IOException {
        InputStream is = new FileInputStream(file);
        try {
            return getAudioInputStream(is);
        } catch (Throwable e) {
            closeQuietly(is, e);
            throw e;
        }
    }

    private InputStream buffered(InputStream is) {
        return is.markSupported() ? is : new BufferedInputStream(is);
    }

    private void closeQuietly(InputStream is, Throwable primary) {
        try {
            is.close();
        } catch (Throwable suppress) {
            primary.addSuppressed(suppress);
        }
    }
}
