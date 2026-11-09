package be.twofold.tinyaudio.core;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

public interface AudioDecoderProvider {

    Optional<AudioDecoder> open(byte[] head, InputStream in) throws IOException;

}
