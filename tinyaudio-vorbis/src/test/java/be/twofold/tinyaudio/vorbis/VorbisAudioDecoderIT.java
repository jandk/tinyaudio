package be.twofold.tinyaudio.vorbis;

import be.twofold.tinyaudio.core.Packet;
import be.twofold.tinyaudio.ogg.OggPacketReader;
import be.twofold.tinyaudio.ogg.OggPageReader;
import com.sun.jna.Memory;
import com.sun.jna.NativeLong;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.PointerByReference;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class VorbisAudioDecoderIT {
    private static final int MAX_FRAMES = 4096;
    private static final float TOLERANCE = 1e-6f;

    private static final LibVorbis LIB_VORBIS = LibVorbis.load();

    private static List<String> vectors() throws IOException {
        Path path = projectRoot().resolve("scripts").resolve("vorbis-vectors.sha256");
        try (Stream<String> lines = Files.lines(path)) {
            return lines
                .filter(line -> !line.isBlank() && !line.startsWith("#"))
                .map(line -> line.strip().split("\\s+\\*?", 2)[1])
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

    private LibVorbis.OggPacket makeOggPacket(Packet packet, long packetNo) {
        byte[] data = packet.getData();
        Memory memory = new Memory(Math.max(1, data.length)); // Memory(0) throws
        memory.write(0, data, 0, data.length);

        LibVorbis.OggPacket oggPacket = new LibVorbis.OggPacket();
        oggPacket.packet = memory;
        oggPacket.bytes = new NativeLong(data.length);
        oggPacket.bos = new NativeLong(packetNo == 0 ? 1 : 0);
        oggPacket.eos = new NativeLong(packet.isFinished() ? 1 : 0);
        oggPacket.granulePos = packet.getPosition(); // -1 except on the last packet of a page, like libogg
        oggPacket.packetNo = packetNo;
        return oggPacket;
    }

    private float[][] decodeLibVorbis(Path path) throws IOException {
        var info = new Memory(4096);
        var comment = new Memory(4096);
        var dsp = new Memory(4096);
        var block = new Memory(4096);

        try (OggPacketReader reader = new OggPacketReader(new OggPageReader(Files.newInputStream(path)))) {
            LIB_VORBIS.vorbis_info_init(info);
            LIB_VORBIS.vorbis_comment_init(comment);

            long packetNo = 0;
            for (int i = 0; i < 3; i++) {
                LIB_VORBIS.vorbis_synthesis_headerin(info, comment, makeOggPacket(reader.next(), packetNo++));
            }

            LIB_VORBIS.vorbis_synthesis_init(dsp, info);
            LIB_VORBIS.vorbis_block_init(dsp, block);

            int channels = info.getInt(4);
            float[][] samples = new float[channels][1 << 16];
            int frames = 0;
            var pcm = new PointerByReference();
            while (true) {
                Packet packet = reader.next();
                if (packet == null) {
                    break;
                }
                if (LIB_VORBIS.vorbis_synthesis(block, makeOggPacket(packet, packetNo++)) != 0) {
                    continue;
                }
                LIB_VORBIS.vorbis_synthesis_blockin(dsp, block);

                while (true) {
                    int n = LIB_VORBIS.vorbis_synthesis_pcmout(dsp, pcm);
                    if (n <= 0) {
                        break;
                    }

                    Pointer[] channelPointers = pcm.getValue().getPointerArray(0, channels);
                    for (int c = 0; c < channels; c++) {
                        if (samples[c].length < frames + n) {
                            samples[c] = Arrays.copyOf(samples[c], Math.max(samples[c].length * 2, frames + n));
                        }
                        channelPointers[c].read(0, samples[c], frames, n);
                    }
                    LIB_VORBIS.vorbis_synthesis_read(dsp, n);
                    frames += n;
                }
            }

            LIB_VORBIS.vorbis_block_clear(block);
            LIB_VORBIS.vorbis_dsp_clear(dsp);
            LIB_VORBIS.vorbis_comment_clear(comment);
            LIB_VORBIS.vorbis_info_clear(info);

            for (int c = 0; c < channels; c++) {
                samples[c] = Arrays.copyOf(samples[c], frames);
            }
            return samples;
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("vectors")
    void decode(String name) throws IOException {
        Path file = projectRoot().resolve("test-vectors").resolve("vorbis").resolve(name);
        assertThat(Files.exists(file)).as("Missing " + name + "; run scripts/vorbis-vectors.sh").isTrue();

        float[][] expected = decodeLibVorbis(file);

        OggPacketReader packets = new OggPacketReader(new OggPageReader(Files.newInputStream(file)));
        VorbisHeader header = VorbisHeader.read(packets.next(), packets);
        try (VorbisAudioDecoder decoder = new VorbisAudioDecoder(packets, header.identification(), header.setup())) {
            int channels = decoder.info().channels();
            assertThat(channels).isEqualTo(expected.length);
            float[][] pcm = new float[channels][MAX_FRAMES];

            int frames = 0;
            while (true) {
                int n = decoder.decodeFloat(pcm);
                if (n < 0) {
                    break;
                }

                assertThat(frames + n).isLessThanOrEqualTo(expected[0].length);
                for (int c = 0; c < channels; c++) {
                    for (int i = 0; i < n; i++) {
                        float expect = expected[c][frames + i];
                        if (Math.abs(pcm[c][i] - expect) > TOLERANCE) {
                            assertThat(pcm[c][i]).isEqualTo(expect);
                        }
                    }
                }
                frames += n;
            }
            assertThat(frames).isEqualTo(expected[0].length);
        }
    }
}
