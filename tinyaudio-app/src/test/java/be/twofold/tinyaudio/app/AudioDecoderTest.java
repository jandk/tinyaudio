package be.twofold.tinyaudio.app;

import be.twofold.tinyaudio.core.AudioDecoder;
import be.twofold.tinyaudio.core.AudioInfo;
import be.twofold.tinyaudio.core.UnsupportedFormatException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Opens every format through service discovery, so it fails when a format module is not registered.
 * The files are a quarter second of a stereo tone, see {@code src/test/resources/formats}.
 */
class AudioDecoderTest {
    private static final byte[] NOT_AUDIO = "This is not an audio file".getBytes(StandardCharsets.US_ASCII);

    @ParameterizedTest
    @CsvSource({
        "tone.flac, flac, flac,   flac, 44100, 11025, 11025",
        "tone.oga,  ogg,  flac,   oga,  44100, 11025, 11025",
        "tone.ogg,  ogg,  vorbis, ogg,  44100,    -1, 11025",
        "tone.opus, ogg,  opus,   opus, 48000,    -1, 12000",
    })
    void open(String file, String container, String codec, String extension, int sampleRate, long totalFrames, long frames) throws Exception {
        try (AudioDecoder decoder = AudioDecoder.open(resource(file))) {
            AudioInfo info = decoder.info();
            assertThat(info.container()).isEqualTo(container);
            assertThat(info.codec()).isEqualTo(codec);
            assertThat(info.extension()).isEqualTo(extension);
            assertThat(info.sampleRate()).isEqualTo(sampleRate);
            assertThat(info.channels()).isEqualTo(2);
            assertThat(info.totalFrames()).isEqualTo(totalFrames);
            assertThat(decodeAll(decoder)).isEqualTo(frames);
        }
    }

    @ParameterizedTest
    @CsvSource({
        "tone.flac, 44100, 11025",
        "tone.oga,  44100, 11025",
        "tone.ogg,  44100, 11025",
        "tone.opus, 48000, 12000",
    })
    void audioSystem(String file, float sampleRate, long frames) throws Exception {
        try (AudioInputStream in = AudioSystem.getAudioInputStream(resource(file).toFile())) {
            assertThat(in.getFormat().getSampleRate()).isEqualTo(sampleRate);
            assertThat(in.getFormat().getChannels()).isEqualTo(2);
            assertThat(in.getFormat().getSampleSizeInBits()).isEqualTo(16);
            assertThat(in.readAllBytes()).hasSize((int) frames * 4);
        }
    }

    @Test
    void openUnknownFormat() {
        assertThatThrownBy(() -> AudioDecoder.open(new ByteArrayInputStream(NOT_AUDIO)))
            .isInstanceOf(UnsupportedFormatException.class);
    }

    @Test
    void audioSystemUnknownFormat() {
        InputStream in = new BufferedInputStream(new ByteArrayInputStream(NOT_AUDIO));
        assertThatThrownBy(() -> AudioSystem.getAudioInputStream(in))
            .isInstanceOf(UnsupportedAudioFileException.class);
    }

    private static long decodeAll(AudioDecoder decoder) throws IOException {
        short[][] pcm = new short[decoder.info().channels()][4096];
        long total = 0;
        for (int n; (n = decoder.read(pcm, 0, 4096)) >= 0; ) {
            total += n;
        }
        return total;
    }

    private static Path resource(String name) throws URISyntaxException {
        return Path.of(AudioDecoderTest.class.getResource("/formats/" + name).toURI());
    }
}
