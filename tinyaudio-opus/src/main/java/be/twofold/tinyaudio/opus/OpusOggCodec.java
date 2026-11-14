package be.twofold.tinyaudio.opus;

import be.twofold.tinyaudio.core.AudioDecoder;
import be.twofold.tinyaudio.core.Packet;
import be.twofold.tinyaudio.core.PacketSource;
import be.twofold.tinyaudio.core.internal.IOUtils;
import be.twofold.tinyaudio.ogg.OggCodec;

import java.io.IOException;
import java.util.Optional;

public final class OpusOggCodec implements OggCodec {
    @Override
    public Optional<AudioDecoder> open(Packet first, PacketSource packets) throws IOException {
        if (!IOUtils.startsWith(first.data(), OpusHeader.MAGIC)) {
            return Optional.empty();
        }
        OpusHeader header = OpusHeader.read(first, packets);
        return Optional.of(new OpusAudioDecoder(packets, header.identification()));
    }
}
