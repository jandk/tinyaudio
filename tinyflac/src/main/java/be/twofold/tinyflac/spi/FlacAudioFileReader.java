package be.twofold.tinyflac.spi;

import be.twofold.tinyflac.FlacException;
import be.twofold.tinyflac.FlacInputStream;
import be.twofold.tinyflac.metadata.StreamInfo;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

public final class FlacAudioFileReader extends AbstractAudioFileReader {
    static final AudioFormat.Encoding ENCODING = new AudioFormat.Encoding("FLAC");
    static final AudioFileFormat.Type TYPE = new AudioFileFormat.Type("FLAC", "flac");
    static final String PROPERTY_STREAM_INFO = "flac.streamInfo";

    @Override
    public AudioFileFormat readAudioFileFormat(InputStream stream, boolean full)
        throws UnsupportedAudioFileException, IOException {

        StreamInfo streamInfo;
        try {
            streamInfo = full
                ? FlacInputStream.open(stream).streamInfo()
                : FlacInputStream.readStreamInfo(stream);
        } catch (FlacException e) { // TODO: Check this exception later
            throw new UnsupportedAudioFileException("Not a FLAC stream");
        }

        AudioFormat audioFormat = new AudioFormat(
            ENCODING,
            streamInfo.sampleRate(),
            streamInfo.bitsPerSample(),
            streamInfo.channels(),
            AudioSystem.NOT_SPECIFIED,
            AudioSystem.NOT_SPECIFIED,
            false,
            Map.of(PROPERTY_STREAM_INFO, streamInfo)
        );

        long totalSamples = streamInfo.totalSamples();
        int frameLength = totalSamples <= 0 || totalSamples > Integer.MAX_VALUE
            ? AudioSystem.NOT_SPECIFIED
            : (int) totalSamples;

        return new AudioFileFormat(TYPE, audioFormat, frameLength);
    }
}
