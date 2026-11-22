package be.twofold.tinyaudio.core;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

/**
 * Detects a format and opens a decoder for it.
 *
 * <p>Implementations are loaded with {@link java.util.ServiceLoader}, and need a public no-argument constructor.
 * {@link AudioDecoder#open(InputStream)} asks each provider in turn, until one returns a decoder.
 */
public interface AudioDecoderProvider {

    /**
     * Opens a decoder if the stream is in this provider's format.
     *
     * <p>Check {@code head} first where possible, and only read from {@code in} when the format looks right. A
     * provider may still read up to 128 KiB and then decline, the caller resets the stream before asking the next
     * provider. A provider must not close the stream when it declines.
     *
     * @param head the first 256 bytes of the stream, or fewer if it is shorter
     * @param in   the stream, at its start, so {@code head} is read again
     * @return a decoder that owns the stream, or empty if the format is not recognized
     * @throws IOException if the format is recognized, but reading fails or the data is invalid
     */
    Optional<AudioDecoder> open(byte[] head, InputStream in) throws IOException;

}
