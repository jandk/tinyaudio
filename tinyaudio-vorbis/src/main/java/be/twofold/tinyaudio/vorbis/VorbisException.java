package be.twofold.tinyaudio.vorbis;

import java.io.IOException;

public final class VorbisException extends IOException {
    public VorbisException(String message) {
        super(message);
    }

    public VorbisException(String message, Throwable cause) {
        super(message, cause);
    }
}
