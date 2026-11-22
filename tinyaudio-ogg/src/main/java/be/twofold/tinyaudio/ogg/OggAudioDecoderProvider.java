package be.twofold.tinyaudio.ogg;

import be.twofold.tinyaudio.core.AudioDecoder;
import be.twofold.tinyaudio.core.AudioDecoderProvider;
import be.twofold.tinyaudio.core.internal.IOUtils;
import be.twofold.tinyaudio.core.internal.Packet;
import be.twofold.tinyaudio.ogg.internal.OggCodec;
import be.twofold.tinyaudio.ogg.internal.OggPacketReader;
import be.twofold.tinyaudio.ogg.internal.OggPage;
import be.twofold.tinyaudio.ogg.internal.OggPageReader;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.stream.Collectors;

public final class OggAudioDecoderProvider implements AudioDecoderProvider {
    private static final List<OggCodec> CODECS = ServiceLoader
        .load(OggCodec.class).stream()
        .map(ServiceLoader.Provider::get)
        .collect(Collectors.toList());

    @Override
    public Optional<AudioDecoder> open(byte[] head, InputStream in) throws IOException {
        if (!IOUtils.startsWith(head, OggPage.MAGIC) || !validFirstPage(head)) {
            return Optional.empty();
        }

        OggPacketReader packets = new OggPacketReader(new OggPageReader(in));
        Packet first = packets.next();
        if (first == null) {
            return Optional.empty();
        }

        for (OggCodec codec : CODECS) {
            Optional<AudioDecoder> decoder = codec.open(first, packets);
            if (decoder.isPresent()) {
                return decoder;
            }
        }
        return Optional.empty();
    }

    private static boolean validFirstPage(byte[] head) {
        if (head.length < 28) {
            return false;
        }
        int packetSize = Byte.toUnsignedInt(head[27]);
        return (head[5] & 0x02) != 0
            && head[26] == 1
            && packetSize != 0
            && packetSize < 255
            && 28 + packetSize <= head.length;
    }
}
