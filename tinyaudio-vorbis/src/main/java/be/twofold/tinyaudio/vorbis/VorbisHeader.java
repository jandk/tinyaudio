package be.twofold.tinyaudio.vorbis;

import be.twofold.tinyaudio.core.Packet;
import be.twofold.tinyaudio.core.PacketSource;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Objects;

final class VorbisHeader {
    private static final byte[] VORBIS = {'v', 'o', 'r', 'b', 'i', 's'};

    private final Identification identification;
    private final Setup setup;

    private VorbisHeader(Identification identification, Setup setup) {
        this.identification = Objects.requireNonNull(identification);
        this.setup = Objects.requireNonNull(setup);
    }

    static VorbisHeader read(Packet first, PacketSource packets) throws IOException {
        Identification identification = Identification.read(header(first, 1));
        header(packets.next(), 3); // skip comment header for now
        Setup setup = Setup.read(header(packets.next(), 5), identification.audioChannels());

        return new VorbisHeader(
            identification,
            setup
        );
    }

    private static ByteBuffer header(Packet packet, int type) throws IOException {
        if (packet == null) {
            throw new VorbisException("Stream ended before header packet of type " + type);
        }

        byte[] data = packet.getData();
        if (data.length < 7 || data[0] != type || !Arrays.equals(data, 1, 7, VORBIS, 0, 6)) {
            throw new VorbisException("Expected header packet of type " + type + ", got " + Util.describe(data));
        }

        return ByteBuffer
            .wrap(data, 7, data.length - 7)
            .order(ByteOrder.LITTLE_ENDIAN);
    }

    Identification identification() {
        return identification;
    }

    Setup setup() {
        return setup;
    }
}
