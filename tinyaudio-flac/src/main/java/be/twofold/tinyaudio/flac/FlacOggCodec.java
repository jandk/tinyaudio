package be.twofold.tinyaudio.flac;

import be.twofold.tinyaudio.core.AudioDecoder;
import be.twofold.tinyaudio.core.Packet;
import be.twofold.tinyaudio.core.PacketSource;
import be.twofold.tinyaudio.core.internal.IOUtils;
import be.twofold.tinyaudio.flac.metadata.StreamInfo;
import be.twofold.tinyaudio.ogg.OggCodec;

import java.io.IOException;
import java.util.Optional;

public final class FlacOggCodec implements OggCodec {
    @Override
    public Optional<AudioDecoder> open(Packet first, PacketSource packets) throws IOException {
        if (!IOUtils.startsWith(first.getData(), FlacHeader.OGG_MAGIC)) {
            return Optional.empty();
        }
        FlacHeader header = FlacHeader.read(first, packets);
        StreamInfo streamInfo = header.streamInfo();
        return Optional.of(new FlacDecoder(packets, streamInfo, "ogg"));
    }
}
