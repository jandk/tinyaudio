package be.twofold.tinyaudio.core;

import java.io.IOException;

/**
 * Thrown when a stream cannot be decoded because of its data, as opposed to a failure of the stream itself.
 *
 * <p>Thrown as is when the data is invalid, like a corrupt header or a failed checksum. The subclass
 * {@link UnsupportedFormatException} is thrown when the data is valid, but uses a format or feature that is not
 * supported.
 */
public class AudioFormatException extends IOException {
    /**
     * Creates an exception.
     *
     * @param message the detail message
     */
    public AudioFormatException(String message) {
        super(message);
    }

    /**
     * Creates an exception with a cause.
     *
     * @param message the detail message
     * @param cause   the cause
     */
    public AudioFormatException(String message, Throwable cause) {
        super(message, cause);
    }
}
