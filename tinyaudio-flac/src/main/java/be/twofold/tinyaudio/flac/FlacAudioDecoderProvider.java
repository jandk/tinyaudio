package be.twofold.tinyaudio.flac;

import be.twofold.tinyaudio.core.AudioDecoder;
import be.twofold.tinyaudio.core.AudioDecoderProvider;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

public final class FlacAudioDecoderProvider implements AudioDecoderProvider {
    private static final byte[] MAGIC = new byte[]{'f', 'L', 'a', 'C'};

    @Override
    public Optional<AudioDecoder> open(byte[] head, InputStream in) throws IOException {
        if (!AudioDecoderProvider.startsWith(head, MAGIC)) {
            return Optional.empty();
        }
        return Optional.of(new FlacDecoder(in, FlacHeader.read(in).streamInfo()));
    }
}
