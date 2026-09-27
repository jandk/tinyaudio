package be.twofold.tinyaudio.flac.spi;

import be.twofold.tinyaudio.flac.FlacDecoder;
import be.twofold.tinyaudio.flac.metadata.StreamInfo;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.IOException;

public final class FlacAudioInputStream extends AudioInputStream {
    private final FlacDecoder decoder;

    public FlacAudioInputStream(AudioInputStream sourceStream, AudioFormat targetFormat) {
        this(sourceStream, targetFormat, streamInfoOf(sourceStream));
    }

    public FlacAudioInputStream(AudioInputStream sourceStream, AudioFormat targetFormat, StreamInfo streamInfo) {
        super(sourceStream, targetFormat, frameLengthOf(streamInfo));
        this.decoder = new FlacDecoder(sourceStream, streamInfo);
    }

    private static StreamInfo streamInfoOf(AudioInputStream sourceStream) {
        Object value = sourceStream.getFormat().getProperty(FlacAudioFileReader.PROPERTY_STREAM_INFO);
        if (!(value instanceof StreamInfo)) {
            throw new IllegalArgumentException("Source stream has no FLAC StreamInfo property");
        }
        return (StreamInfo) value;
    }

    private static long frameLengthOf(StreamInfo streamInfo) {
        long totalSamples = streamInfo.getTotalSamples();
        return totalSamples == 0 ? AudioSystem.NOT_SPECIFIED : totalSamples;
    }

    @Override
    public int read() throws IOException {
        throw new IOException("Cannot read single bytes from a frame-based stream");
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        int frameSize = getFormat().getFrameSize();
        len -= len % frameSize;
        return len == 0 ? 0 : decoder.read(b, off, len);
    }

    @Override
    public long skip(long n) throws IOException {
        return decoder.skip(n - (n % getFormat().getFrameSize()));
    }
}
