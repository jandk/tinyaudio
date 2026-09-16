package be.twofold.tinyogg;

import java.io.IOException;

public final class OggException extends IOException {
    public OggException(String message) {
        super(message);
    }

    public OggException(String message, Throwable cause) {
        super(message, cause);
    }
}
