package be.twofold.tinyaudio.opus;

import be.twofold.tinyaudio.core.UnsupportedFormatException;
import be.twofold.tinyaudio.core.internal.Packet;
import be.twofold.tinyaudio.core.internal.PacketSource;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;

class OpusAudioDecoderIT {
    private static final byte[] OPUS_HEAD = {
        'O', 'p', 'u', 's', 'H', 'e', 'a', 'd',
        1, 2, 0, 0, (byte) 0x80, (byte) 0xBB, 0, 0, 0, 0, 0 // v1, stereo, 48000 Hz, family 0
    };

    private static final Path ROOT = projectRoot().resolve("test-vectors/opus");

    private static List<String> vectors() throws IOException {
        try (Stream<Path> stream = Files.list(ROOT)) {
            return stream
                .map(p -> p.getFileName().toString())
                .filter(s -> s.endsWith(".bit"))
                .map(s -> s.substring(0, s.length() - 4))
                .sorted()
                .collect(Collectors.toList());
        }
    }

    private static Path projectRoot() {
        Path start = Path.of("").toAbsolutePath();
        for (Path path = start; path != null; path = path.getParent()) {
            if (Files.exists(path.resolve(".git"))) {
                return path;
            }
        }
        throw new IllegalStateException("No .git found above " + start);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("vectors")
    void decode(String file) throws IOException {
        int id = Integer.parseInt(file.substring(file.length() - 2));
        if (id == 1 || id == 7 || id == 11) {
            testOpus(file);
        } else { // SILK or hybrid
            skipIfThrows(file, UnsupportedFormatException.class);
        }
    }

    private static void skipIfThrows(String file, Class<? extends Throwable> type) {
        Throwable thrown = catchThrowable(() -> testOpus(file));
        assertThat(thrown).isInstanceOf(type);
        Assumptions.abort(thrown.getMessage());
    }

    private static void testOpus(String file) throws IOException {
        var source = ByteBuffer.wrap(Files.readAllBytes(ROOT.resolve(file + ".bit"))).order(ByteOrder.BIG_ENDIAN);
        var reference = ByteBuffer.wrap(Files.readAllBytes(ROOT.resolve(file + ".dec"))).order(ByteOrder.LITTLE_ENDIAN);

        BitPacketSource packets = new BitPacketSource(source);
        OpusIdentification identification = OpusIdentification.read(ByteBuffer.wrap(OPUS_HEAD));
        try (OpusAudioDecoder decoder = new OpusAudioDecoder(packets, identification)) {
            int channels = decoder.info().channels();
            short[][] pcm = new short[channels][5760];
            int frames = 0;
            int mismatches = 0;
            while (true) {
                int n = decoder.read(pcm, 0, 5760);
                if (n < 0) {
                    break;
                }
                assertThat(decoder.rangeFinal)
                    .as("final range at frame %d", frames)
                    .isEqualTo(packets.rangeFinal);
                for (int i = 0; i < n; i++) {
                    for (int c = 0; c < channels; c++) {
                        short expected = reference.getShort();
                        int diff = Math.abs(pcm[c][i] - expected);
                        if (diff > 1) {
                            fail("Mismatch at frame %d, channel %d: %d != %d", frames + i, c, pcm[c][i], expected);
                        }
                        mismatches += diff;
                    }
                }
                frames += n;
            }
            assertThat(reference.hasRemaining()).isFalse();
            System.out.printf("Mismatch percentage: %.3f%%%n", mismatches * 100.0 / (frames * channels));
        }
    }

    private static final class BitPacketSource implements PacketSource {
        private final ByteBuffer source;
        private int rangeFinal;

        private BitPacketSource(ByteBuffer source) {
            this.source = source;
        }

        @Override
        public Packet next() {
            if (!source.hasRemaining()) {
                return null;
            }

            int length = source.getInt();
            this.rangeFinal = source.getInt();
            byte[] packet = new byte[length];
            source.get(packet);

            return new Packet(
                packet,
                -1,
                false,
                false
            );
        }

        @Override
        public void close() {
        }
    }
}
