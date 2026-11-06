package be.twofold.tinyaudio.flac;

import java.io.IOException;

public final class FlacException extends IOException {
    public FlacException(String message) {
        super(message);
    }

    public FlacException(String message, Throwable cause) {
        super(message, cause);
    }
}
