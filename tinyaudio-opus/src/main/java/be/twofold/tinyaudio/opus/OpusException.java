package be.twofold.tinyaudio.opus;

import java.io.IOException;

/**
 * Thrown when an Opus stream is invalid.
 */
public final class OpusException extends IOException {
    /**
     * Creates an exception.
     *
     * @param message the detail message
     */
    public OpusException(String message) {
        super(message);
    }

    /**
     * Creates an exception with a cause.
     *
     * @param message the detail message
     * @param cause   the cause
     */
    public OpusException(String message, Throwable cause) {
        super(message, cause);
    }
}
