package be.twofold.tinyaudio.flac;

import be.twofold.tinyaudio.core.AudioDecoder;
import be.twofold.tinyaudio.core.internal.IOUtils;
import be.twofold.tinyaudio.core.internal.Packet;
import be.twofold.tinyaudio.core.internal.PacketSource;
import be.twofold.tinyaudio.flac.metadata.StreamInfo;
import be.twofold.tinyaudio.ogg.internal.OggCodec;

import java.io.IOException;
import java.util.Optional;

/**
 * Opens FLAC in Ogg. Used by the Ogg provider.
 */
public final class FlacOggCodec implements OggCodec {
    /**
     * Creates the service. Called by {@link java.util.ServiceLoader}.
     */
    public FlacOggCodec() {
    }

    @Override
    public Optional<AudioDecoder> open(Packet first, PacketSource packets) throws IOException {
        if (!IOUtils.startsWith(first.data(), FlacHeader.OGG_MAGIC)) {
            return Optional.empty();
        }
        FlacHeader header = FlacHeader.read(first, packets);
        StreamInfo streamInfo = header.streamInfo();
        return Optional.of(new FlacAudioDecoder(packets, streamInfo, "ogg"));
    }
}
