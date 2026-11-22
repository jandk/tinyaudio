package be.twofold.tinyaudio.ogg;

import java.io.IOException;

/**
 * Thrown when an Ogg stream is invalid.
 */
public final class OggException extends IOException {
    /**
     * Creates an exception.
     *
     * @param message the detail message
     */
    public OggException(String message) {
        super(message);
    }
}
