package be.twofold.tinyaudio.vorbis;

import be.twofold.tinyaudio.core.AudioDecoder;
import be.twofold.tinyaudio.core.internal.Packet;
import be.twofold.tinyaudio.core.internal.PacketSource;
import be.twofold.tinyaudio.ogg.internal.OggCodec;

import java.io.IOException;
import java.util.Optional;

/**
 * Opens Vorbis in Ogg. Used by the Ogg provider.
 */
public final class VorbisOggCodec implements OggCodec {
    /**
     * Creates the service. Called by {@link java.util.ServiceLoader}.
     */
    public VorbisOggCodec() {
    }

    @Override
    public Optional<AudioDecoder> open(Packet first, PacketSource packets) throws IOException {
        if (!VorbisHeader.isHeader(first.data(), 1)) {
            return Optional.empty();
        }
        VorbisHeader header = VorbisHeader.read(first, packets);
        return Optional.of(new VorbisAudioDecoder(packets, header.identification(), header.setup()));
    }
}
