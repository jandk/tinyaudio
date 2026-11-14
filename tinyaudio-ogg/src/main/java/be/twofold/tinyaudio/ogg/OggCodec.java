package be.twofold.tinyaudio.ogg;

import be.twofold.tinyaudio.core.AudioDecoder;
import be.twofold.tinyaudio.core.Packet;
import be.twofold.tinyaudio.core.PacketSource;

import java.io.IOException;
import java.util.Optional;

public interface OggCodec {
    Optional<AudioDecoder> open(Packet first, PacketSource packets) throws IOException;
}
