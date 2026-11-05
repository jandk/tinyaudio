package be.twofold.tinyaudio.core;

import java.io.IOException;
import java.io.InputStream;

public interface AudioDecoderProvider {

    boolean matches(byte[] head);

    AudioDecoder open(InputStream in) throws IOException;

}
