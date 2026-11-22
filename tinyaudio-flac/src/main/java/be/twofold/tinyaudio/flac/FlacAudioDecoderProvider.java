package be.twofold.tinyaudio.flac;

import be.twofold.tinyaudio.core.AudioDecoder;
import be.twofold.tinyaudio.core.AudioDecoderProvider;
import be.twofold.tinyaudio.core.internal.IOUtils;
import be.twofold.tinyaudio.flac.metadata.StreamInfo;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

/**
 * Opens native FLAC streams.
 */
public final class FlacAudioDecoderProvider implements AudioDecoderProvider {
    /**
     * Creates the service. Called by {@link java.util.ServiceLoader}.
     */
    public FlacAudioDecoderProvider() {
    }


    @Override
    public Optional<AudioDecoder> open(byte[] head, InputStream in) throws IOException {
        if (!IOUtils.startsWith(head, FlacHeader.MAGIC)) {
            return Optional.empty();
        }
        FlacHeader header = FlacHeader.read(in);
        StreamInfo streamInfo = header.streamInfo();
        return Optional.of(new FlacAudioDecoder(new FlacPacketSource(in, streamInfo), streamInfo, "flac"));
    }
}
