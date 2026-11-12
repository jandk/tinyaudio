package be.twofold.tinyaudio.opus;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

import static org.assertj.core.api.Assertions.assertThat;

class OpusAudioDecoderTest {
    private static final byte[] OPUS_HEAD = {
        'O', 'p', 'u', 's', 'H', 'e', 'a', 'd',
        1, 2, 0, 0, (byte) 0x80, (byte) 0xBB, 0, 0, 0, 0, 0 // v1, stereo, 48000 Hz, family 0
    };

    @Test
    void testVector01() throws IOException {
        ByteBuffer bit = ByteBuffer.wrap(readResource("/testvector01.bit")); // big-endian by default
        OpusAudioDecoder decoder = new OpusAudioDecoder(new EmptySource(),
            OpusIdentification.read(ByteBuffer.wrap(OPUS_HEAD)));
        short[][] pcm = new short[2][5760];

        int packets = 0;
        int samples = 0;
        while (bit.hasRemaining()) {
            int len = bit.getInt();
            int rangeFinal = bit.getInt();
            byte[] data = new byte[len];
            bit.get(data);

            samples += decoder.read(pcm, 0, 5760);
            assertThat(decoder.rangeFinal).as("final range of packet %d", packets).isEqualTo(rangeFinal);
            packets++;
        }

        assertThat(packets).isEqualTo(2147);
        assertThat(samples).isEqualTo(1415040);
    }

    private static byte[] readResource(String name) throws IOException {
        try (InputStream in = OpusAudioDecoderTest.class.getResourceAsStream(name)) {
            assertThat(in).as("resource %s", name).isNotNull();
            return in.readAllBytes();
        }
    }
}
