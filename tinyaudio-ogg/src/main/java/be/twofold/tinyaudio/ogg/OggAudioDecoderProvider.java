package be.twofold.tinyaudio.ogg;

import be.twofold.tinyaudio.core.AudioDecoder;
import be.twofold.tinyaudio.core.AudioDecoderProvider;
import be.twofold.tinyaudio.core.Packet;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.stream.Collectors;

public final class OggAudioDecoderProvider implements AudioDecoderProvider {
    private static final byte[] MAGIC = new byte[]{'O', 'g', 'g', 'S'};
    private static final List<OggCodec> CODECS = ServiceLoader
            .load(OggCodec.class).stream()
            .map(ServiceLoader.Provider::get)
            .collect(Collectors.toList());

    @Override
    public Optional<AudioDecoder> open(byte[] head, InputStream in) throws IOException {
        if (!AudioDecoderProvider.startsWith(head, MAGIC)) {
            return Optional.empty();
        }

        OggPacketReader reader = new OggPacketReader(new OggPageReader(in));
        Packet first = reader.next();
        if (first == null) {
            return Optional.empty();
        }

        for (OggCodec codec : CODECS) {
            Optional<AudioDecoder> decoder = codec.open(first, reader);
            if (decoder.isPresent()) {
                return decoder;
            }
        }
        return Optional.empty();
    }
}
