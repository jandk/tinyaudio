package be.twofold.tinyaudio.vorbis;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteOrder;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class SetupHeaderTest {
    private static final int AUDIO_CHANNELS = 2;
    private static final byte[] PACKET_HEADER = {0x05, 'v', 'o', 'r', 'b', 'i', 's'};

    @Test
    void testRead() throws IOException {
        byte[] packet = readResource("/setup.bin");
        assertThat(Arrays.copyOf(packet, PACKET_HEADER.length)).isEqualTo(PACKET_HEADER);

        BitReader reader = new BitReader(packet, PACKET_HEADER.length, packet.length - PACKET_HEADER.length, ByteOrder.LITTLE_ENDIAN);
        SetupHeader setupHeader = SetupHeader.read(reader, AUDIO_CHANNELS);

        assertThat(reader.isOverrun()).isFalse();
        assertThat(reader.bitsRemaining()).isLessThan(8);

        assertThat(setupHeader.codebooks()).hasSize(42);
        assertThat(setupHeader.floors()).hasSize(2);
        assertThat(setupHeader.residues()).hasSize(2);
        assertThat(setupHeader.mappings()).hasSize(2);
        assertThat(setupHeader.modes()).hasSize(2);
    }

    private static byte[] readResource(String name) throws IOException {
        try (InputStream in = SetupHeaderTest.class.getResourceAsStream(name)) {
            assertThat(in).as("resource %s", name).isNotNull();
            return in.readAllBytes();
        }
    }
}
