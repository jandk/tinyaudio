package be.twofold.tinyaudio.vorbis;

import be.twofold.tinyaudio.core.AudioDecoder;
import be.twofold.tinyaudio.core.Packet;
import be.twofold.tinyaudio.core.PacketSource;
import be.twofold.tinyaudio.ogg.OggCodec;

import java.io.IOException;
import java.util.Optional;

public final class VorbisOggCodec implements OggCodec {
    @Override
    public Optional<AudioDecoder> open(Packet first, PacketSource packets) throws IOException {
        if (!VorbisHeader.isHeader(first.getData(), 1)) {
            return Optional.empty();
        }
        VorbisHeader header = VorbisHeader.read(first, packets);
        return Optional.of(new VorbisAudioDecoder(packets, header.identification(), header.setup()));
    }
}
