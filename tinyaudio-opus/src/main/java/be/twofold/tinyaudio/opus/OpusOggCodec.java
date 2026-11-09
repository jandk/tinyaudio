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
        if (!IOUtils.startsWith(first.getData(), OpusHeader.MAGIC)) {
            return Optional.empty();
        }
        OpusHeader header = OpusHeader.read(first, packets);
        System.out.println("header.identification() = " + header.identification());
        throw new OpusException("Opus decoding is not implemented yet");
    }
}
