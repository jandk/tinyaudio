package be.twofold.tinyaudio.vorbis;

import be.twofold.tinyaudio.core.AudioDecoder;
import be.twofold.tinyaudio.core.AudioDecoderProvider;
import be.twofold.tinyaudio.core.Packet;
import be.twofold.tinyaudio.core.PacketSource;
import be.twofold.tinyaudio.ogg.OggCodec;

import java.io.IOException;
import java.util.Optional;

public final class VorbisOggCodec implements OggCodec {
    private static final byte[] MAGIC = {0x01, 'v', 'o', 'r', 'b', 'i', 's'};

    @Override
    public Optional<AudioDecoder> open(Packet first, PacketSource packets) throws IOException {
        if (!AudioDecoderProvider.startsWith(first.getData(), MAGIC)) {
            return Optional.empty();
        }
        return Optional.of(VorbisDecoder.create(first, packets));
    }
}
