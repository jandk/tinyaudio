package be.twofold.tinyaudio.flac;

import java.io.IOException;

/**
 * Thrown when a FLAC stream is invalid.
 */
public final class FlacException extends IOException {
    /**
     * Creates an exception.
     *
     * @param message the detail message
     */
    public FlacException(String message) {
        super(message);
    }

    /**
     * Creates an exception with a cause.
     *
     * @param message the detail message
     * @param cause   the cause
     */
    public FlacException(String message, Throwable cause) {
        super(message, cause);
    }
}
