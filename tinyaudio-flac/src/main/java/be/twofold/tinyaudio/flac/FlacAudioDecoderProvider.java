package be.twofold.tinyaudio.flac;

import be.twofold.tinyaudio.core.AudioDecoder;
import be.twofold.tinyaudio.core.AudioDecoderProvider;
import be.twofold.tinyaudio.flac.metadata.Metadata;

import java.io.IOException;
import java.io.InputStream;

public final class FlacAudioDecoderProvider implements AudioDecoderProvider {
    @Override
    public boolean matches(byte[] head) {
        return head.length >= 4
            && head[0] == 'f'
            && head[1] == 'L'
            && head[2] == 'a'
            && head[3] == 'C';
    }

    @Override
    public AudioDecoder open(InputStream in) throws IOException {
        return new FlacDecoder(in, Metadata.read(in).getStreamInfo());
    }
}
