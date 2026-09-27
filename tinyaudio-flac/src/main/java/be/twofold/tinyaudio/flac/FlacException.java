package be.twofold.tinyaudio.flac;

public class FlacException extends RuntimeException {
    public FlacException(String message) {
        super(message);
    }

    public FlacException(String message, Throwable cause) {
        super(message, cause);
    }
}
