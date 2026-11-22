package be.twofold.tinyaudio.vorbis;

import java.io.IOException;

/**
 * Thrown when a Vorbis stream is invalid.
 */
public final class VorbisException extends IOException {
    /**
     * Creates an exception.
     *
     * @param message the detail message
     */
    public VorbisException(String message) {
        super(message);
    }

    /**
     * Creates an exception with a cause.
     *
     * @param message the detail message
     * @param cause   the cause
     */
    public VorbisException(String message, Throwable cause) {
        super(message, cause);
    }
}
