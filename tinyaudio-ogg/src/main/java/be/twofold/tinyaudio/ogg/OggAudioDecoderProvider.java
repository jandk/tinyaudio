package be.twofold.tinyaudio.ogg;

import be.twofold.tinyaudio.core.AudioDecoder;
import be.twofold.tinyaudio.core.AudioDecoderProvider;
import be.twofold.tinyaudio.core.Packet;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

public final class OggAudioDecoderProvider implements AudioDecoderProvider {
    @Override
    public boolean matches(byte[] head) {
        try {
            OggPacketReader reader = new OggPacketReader(new OggPageReader(new ByteArrayInputStream(head)));
            Packet first = reader.next();
            return first != null && OggCodecs.findCodec(first).isPresent();
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    public AudioDecoder open(InputStream in) throws IOException {
        OggPacketReader reader = new OggPacketReader(new OggPageReader(in));
        Packet first = reader.next();
        if (first == null) {
            throw new OggException("Stream has no packets");
        }
        OggCodec codec = OggCodecs.findCodec(first)
            .orElseThrow(() -> new OggException("Unsupported Ogg codec"));
        return codec.open(first, reader);
    }
}
