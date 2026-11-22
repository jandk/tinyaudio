package be.twofold.tinyaudio.opus;

import be.twofold.tinyaudio.core.AudioDecoder;
import be.twofold.tinyaudio.core.internal.IOUtils;
import be.twofold.tinyaudio.core.internal.Packet;
import be.twofold.tinyaudio.core.internal.PacketSource;
import be.twofold.tinyaudio.ogg.internal.OggCodec;

import java.io.IOException;
import java.util.Optional;

/**
 * Opens Opus in Ogg. Used by the Ogg provider.
 */
public final class OpusOggCodec implements OggCodec {
    /**
     * Creates the service. Called by {@link java.util.ServiceLoader}.
     */
    public OpusOggCodec() {
    }

    @Override
    public Optional<AudioDecoder> open(Packet first, PacketSource packets) throws IOException {
        if (!IOUtils.startsWith(first.data(), OpusHeader.MAGIC)) {
            return Optional.empty();
        }
        OpusHeader header = OpusHeader.read(first, packets);
        return Optional.of(new OpusAudioDecoder(packets, header.identification()));
    }
}
