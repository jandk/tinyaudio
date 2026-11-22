package be.twofold.tinyaudio.core.internal;

import be.twofold.tinyaudio.core.AudioDecoder;
import be.twofold.tinyaudio.core.AudioDecoderProvider;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.ServiceLoader;

public final class DecoderDetector {
    private static final int HEAD_SIZE = 256;

    // Enough for the largest Ogg page, which a provider may buffer before deciding
    public static final int MARK_LIMIT = 1 << 17;

    private DecoderDetector() {
    }

    /**
     * Finds a decoder for a stream that is already marked.
     * The stream is reset to the mark after every provider that declines.
     */
    public static Optional<AudioDecoder> detect(InputStream in) throws IOException {
        byte[] head = in.readNBytes(HEAD_SIZE);
        in.reset();

        for (AudioDecoderProvider provider : ServiceLoader.load(AudioDecoderProvider.class)) {
            Optional<AudioDecoder> decoder = provider.open(head, in);
            if (decoder.isPresent()) {
                return decoder;
            }
            in.reset();
        }
        return Optional.empty();
    }
}
