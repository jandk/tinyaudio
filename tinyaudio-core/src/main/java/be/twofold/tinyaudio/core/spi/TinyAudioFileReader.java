package be.twofold.tinyaudio.core.spi;

import be.twofold.tinyaudio.core.AudioDecoder;
import be.twofold.tinyaudio.core.AudioDecoderProvider;
import be.twofold.tinyaudio.core.AudioInfo;

import javax.sound.sampled.*;
import javax.sound.sampled.spi.AudioFileReader;
import java.io.*;
import java.net.URL;
import java.util.ServiceLoader;

public final class TinyAudioFileReader extends AudioFileReader {
    private static final int HEAD_SIZE = 64;
    private static final int MARK_LIMIT = 1 << 24; // Largest FLAC metadata block

    @Override
    public AudioFileFormat getAudioFileFormat(InputStream stream)
        throws UnsupportedAudioFileException, IOException {
        try {
            return fileFormat(open(stream).info());
        } finally {
            stream.reset();
        }
    }

    @Override
    public AudioFileFormat getAudioFileFormat(URL url)
        throws UnsupportedAudioFileException, IOException {
        try (InputStream in = new BufferedInputStream(url.openStream())) {
            return fileFormat(open(in).info());
        }
    }

    @Override
    public AudioFileFormat getAudioFileFormat(File file)
        throws UnsupportedAudioFileException, IOException {
        try (InputStream in = new BufferedInputStream(new FileInputStream(file))) {
            return fileFormat(open(in).info());
        }
    }

    @Override
    public AudioInputStream getAudioInputStream(InputStream stream)
        throws UnsupportedAudioFileException, IOException {
        AudioDecoder decoder = open(stream);
        AudioInfo info = decoder.info();
        return new AudioInputStream(new AudioDecoderInputStream(decoder), audioFormat(info), info.totalFrames());
    }

    @Override
    public AudioInputStream getAudioInputStream(URL url)
        throws UnsupportedAudioFileException, IOException {
        return openOwned(url.openStream());
    }

    @Override
    public AudioInputStream getAudioInputStream(File file)
        throws UnsupportedAudioFileException, IOException {
        return openOwned(new FileInputStream(file));
    }

    private AudioDecoder open(InputStream in)
        throws UnsupportedAudioFileException, IOException {
        if (!in.markSupported()) {
            throw new IOException("Stream does not support mark/reset");
        }

        in.mark(MARK_LIMIT);
        byte[] head = in.readNBytes(HEAD_SIZE);
        in.reset();

        for (AudioDecoderProvider provider : ServiceLoader.load(AudioDecoderProvider.class)) {
            if (provider.matches(head)) {
                return provider.open(in);
            }
        }
        throw new UnsupportedAudioFileException("No decoder found for stream");
    }

    private AudioInputStream openOwned(InputStream in)
        throws UnsupportedAudioFileException, IOException {
        try {
            return getAudioInputStream(new BufferedInputStream(in));
        } catch (Exception e) {
            in.close();
            throw e;
        }
    }

    private AudioFileFormat fileFormat(AudioInfo info) {
        long totalFrames = info.totalFrames();
        int frameLength = totalFrames < 0 || totalFrames > Integer.MAX_VALUE
            ? AudioSystem.NOT_SPECIFIED
            : (int) totalFrames;

        AudioFileFormat.Type type = new AudioFileFormat.Type(info.codec(), info.extension());
        return new AudioFileFormat(type, audioFormat(info), frameLength);
    }

    private AudioFormat audioFormat(AudioInfo info) {
        return new AudioFormat(info.sampleRate(), 16, info.channels(), true, false);
    }
}
