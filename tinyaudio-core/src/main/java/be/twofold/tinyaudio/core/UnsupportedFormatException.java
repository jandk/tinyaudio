package be.twofold.tinyaudio.core;

/**
 * Thrown when a stream is valid, but uses a format or feature that is not supported. For example, when no decoder
 * recognizes the stream, or an Opus stream contains SILK packets.
 */
public final class UnsupportedFormatException extends AudioFormatException {
    /**
     * Creates an exception.
     *
     * @param message the detail message
     */
    public UnsupportedFormatException(String message) {
        super(message);
    }
}
