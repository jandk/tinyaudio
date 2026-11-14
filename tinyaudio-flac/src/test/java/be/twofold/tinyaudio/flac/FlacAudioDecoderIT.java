package be.twofold.tinyaudio.flac;

import be.twofold.tinyaudio.flac.metadata.StreamInfo;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

class FlacAudioDecoderIT {
    private static final Path ROOT = projectRoot().resolve("test-vectors/flac");

    private static Path projectRoot() {
        Path start = Path.of("").toAbsolutePath();
        for (Path path = start; path != null; path = path.getParent()) {
            if (Files.exists(path.resolve(".git"))) {
                return path;
            }
        }
        throw new IllegalStateException("No .git found above " + start);
    }

    private static List<String> subset() throws IOException {
        return list("subset");
    }

    private static List<String> uncommon() throws IOException {
        return list("uncommon");
    }

    private static List<String> list(String category) throws IOException {
        try (Stream<Path> stream = Files.list(ROOT.resolve(category))) {
            return stream
                .map(p -> category + "/" + p.getFileName())
                .filter(s -> s.endsWith(".flac"))
                .sorted()
                .collect(Collectors.toList());
        }
    }

    private static void testFlac(String file) throws IOException, NoSuchAlgorithmException {
        try (InputStream in = Files.newInputStream(ROOT.resolve(file))) {
            StreamInfo info = FlacHeader.read(in).streamInfo();
            try (FlacAudioDecoder decoder = new FlacAudioDecoder(new FlacPacketSource(in, info), info, "flac")) {
                MessageDigest md5 = MessageDigest.getInstance("MD5");
                int channels = info.channels();
                int bytes = (info.bitsPerSample() + 7) / 8;
                int[][] pcm = new int[channels][info.maxBlockSizeOrDefault()];
                byte[] buffer = new byte[pcm[0].length * channels * bytes];

                for (int n; (n = decoder.decodeInt(pcm)) >= 0; ) {
                    int pos = 0;
                    for (int i = 0; i < n; i++) {
                        for (int c = 0; c < channels; c++) {
                            for (int b = 0; b < bytes; b++) {
                                buffer[pos++] = (byte) (pcm[c][i] >> (8 * b));
                            }
                        }
                    }
                    md5.update(buffer, 0, pos);
                }
                assertThat(md5.digest()).isEqualTo(info.md5Sum());
            }
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("subset")
    void decodeSubset(String file) throws Exception {
        testFlac(file);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("uncommon")
    void decodeUncommon(String file) throws Exception {
        int index = file.lastIndexOf('/');
        int id = Integer.parseInt(file.substring(index + 1, index + 1 + 2));
        if (id >= 1 && id <= 4 || id == 5) { // unsupported features
            skipIfThrows(file, UnsupportedOperationException.class);
        } else if (id >= 10 && id <= 11) { // no streaminfo
            skipIfThrows(file, FlacException.class);
        } else {
            testFlac(file);
        }
    }

    private static void skipIfThrows(String file, Class<? extends Throwable> type) {
        Throwable thrown = catchThrowable(() -> testFlac(file));
        assertThat(thrown).isInstanceOf(type);
        Assumptions.abort(thrown.getMessage());
    }
}
