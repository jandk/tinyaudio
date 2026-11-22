package be.twofold.tinyaudio.opus;

import be.twofold.tinyaudio.core.AudioDecoder;
import be.twofold.tinyaudio.ogg.internal.OggPacketReader;
import be.twofold.tinyaudio.ogg.internal.OggPageReader;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

class OpusOggIT {
    private static final int MAX_FRAMES = 5760;

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"mono", "stereo", "51", "71", "clip"})
    void decode(String name) throws IOException {
        ByteBuffer reference = ByteBuffer.wrap(readResource(name + ".pcm")).order(ByteOrder.LITTLE_ENDIAN);

        InputStream in = new ByteArrayInputStream(readResource(name + ".opus"));
        OggPacketReader packets = new OggPacketReader(new OggPageReader(in));
        try (AudioDecoder decoder = new OpusOggCodec().open(packets.next(), packets).orElseThrow()) {
            int channels = decoder.info().channels();
            int expected = reference.capacity() / 2 / channels;
            short[][] pcm = new short[channels][MAX_FRAMES];

            int frames = 0;
            for (int n; (n = decoder.read(pcm, 0, MAX_FRAMES)) >= 0; ) {
                assertThat(frames + n)
                    .as("decoded frames, reference has %d", expected)
                    .isLessThanOrEqualTo(expected);
                for (int i = 0; i < n; i++) {
                    for (int c = 0; c < channels; c++) {
                        short sample = reference.getShort();
                        if (Math.abs(pcm[c][i] - sample) > 1) {
                            fail("Mismatch at frame %d, channel %d: %d != %d", frames + i, c, pcm[c][i], sample);
                        }
                    }
                }
                frames += n;
            }
            assertThat(frames).isEqualTo(expected);
        }
    }

    private static byte[] readResource(String name) throws IOException {
        try (InputStream in = OpusOggIT.class.getResourceAsStream("/ogg/" + name)) {
            assertThat(in).as("resource ogg/%s", name).isNotNull();
            return in.readAllBytes();
        }
    }
}
