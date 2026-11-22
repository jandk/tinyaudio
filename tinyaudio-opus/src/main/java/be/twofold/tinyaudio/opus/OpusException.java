package be.twofold.tinyaudio.opus;

import java.io.IOException;

public final class OpusException extends IOException {
    public OpusException(String message) {
        super(message);
    }

    public OpusException(String message, Throwable cause) {
        super(message, cause);
    }
}
