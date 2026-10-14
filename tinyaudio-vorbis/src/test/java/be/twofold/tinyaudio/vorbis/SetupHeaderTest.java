package be.twofold.tinyaudio.vorbis;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteOrder;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class SetupHeaderTest {
    private static final int AUDIO_CHANNELS = 2;
    private static final byte[] PACKET_HEADER = {0x05, 'v', 'o', 'r', 'b', 'i', 's'};

    @Test
    void testRoundTrip() throws IOException {
        byte[] packet = readResource("/setup.bin");
        assertThat(Arrays.copyOf(packet, PACKET_HEADER.length)).isEqualTo(PACKET_HEADER);

        byte[] expected = Arrays.copyOfRange(packet, PACKET_HEADER.length, packet.length);

        BitReader bits = new BitReader(expected, ByteOrder.LITTLE_ENDIAN);
        SetupHeader setupHeader = SetupHeader.read(bits, AUDIO_CHANNELS);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (BitOutputStream bitsOut = new BitOutputStream(out, ByteOrder.LITTLE_ENDIAN)) {
            setupHeader.write(bitsOut, AUDIO_CHANNELS);
        }

        assertThat(out.toByteArray()).isEqualTo(expected);
    }

    private static byte[] readResource(String name) throws IOException {
        try (InputStream in = SetupHeaderTest.class.getResourceAsStream(name)) {
            assertThat(in).as("resource %s", name).isNotNull();
            return in.readAllBytes();
        }
    }
}
