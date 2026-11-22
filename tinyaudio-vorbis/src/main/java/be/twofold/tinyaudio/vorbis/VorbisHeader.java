package be.twofold.tinyaudio.vorbis;

import be.twofold.tinyaudio.core.internal.IOUtils;
import be.twofold.tinyaudio.core.internal.Packet;
import be.twofold.tinyaudio.core.internal.PacketSource;
import be.twofold.tinyaudio.core.internal.VorbisComment;
import be.twofold.tinyaudio.vorbis.internal.Identification;
import be.twofold.tinyaudio.vorbis.internal.Setup;

import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Map;
import java.util.Objects;

final class VorbisHeader {
    private static final byte[] MAGIC = {'v', 'o', 'r', 'b', 'i', 's'};

    private final Identification identification;
    private final VorbisComment comment;
    private final Setup setup;

    private VorbisHeader(Identification identification, VorbisComment comment, Setup setup) {
        this.identification = Objects.requireNonNull(identification);
        this.comment = Objects.requireNonNull(comment);
        this.setup = Objects.requireNonNull(setup);
    }

    static VorbisHeader read(Packet first, PacketSource packets) throws IOException {
        Identification identification = Identification.read(header(first, 1));
        VorbisComment comment = readComment(packets);
        Setup setup = Setup.read(header(packets.next(), 5), identification.channels());

        return new VorbisHeader(
            identification,
            comment,
            setup
        );
    }

    static boolean isHeader(byte[] data, int type) {
        ByteBuffer buffer = ByteBuffer.wrap(data);
        return buffer.hasRemaining() && buffer.get() == type && IOUtils.matches(buffer, MAGIC);
    }

    private static ByteBuffer header(Packet packet, int type) throws IOException {
        if (packet == null) {
            throw new VorbisException("Stream ended before header packet of type " + type);
        }
        if (!isHeader(packet.data(), type)) {
            throw new VorbisException("Expected header packet of type " + type);
        }

        return ByteBuffer.wrap(packet.data())
            .position(1 + MAGIC.length)
            .order(ByteOrder.LITTLE_ENDIAN);
    }

    private static VorbisComment readComment(PacketSource packets) throws IOException {
        ByteBuffer buffer = header(packets.next(), 3);
        try {
            return VorbisComment.read(buffer);
        } catch (BufferUnderflowException | IOException e) {
            // failed metadata shouldn't cause an exception
            return new VorbisComment("", Map.of());
        }
    }

    Identification identification() {
        return identification;
    }

    VorbisComment comment() {
        return comment;
    }

    Setup setup() {
        return setup;
    }
}
