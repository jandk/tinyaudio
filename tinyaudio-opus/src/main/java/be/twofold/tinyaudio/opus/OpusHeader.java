package be.twofold.tinyaudio.opus;

import be.twofold.tinyaudio.core.internal.IOUtils;
import be.twofold.tinyaudio.core.internal.Packet;
import be.twofold.tinyaudio.core.internal.PacketSource;
import be.twofold.tinyaudio.core.internal.VorbisComment;

import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Map;
import java.util.Objects;

final class OpusHeader {
    static final byte[] MAGIC = {'O', 'p', 'u', 's', 'H', 'e', 'a', 'd'};
    static final byte[] TAGS_MAGIC = {'O', 'p', 'u', 's', 'T', 'a', 'g', 's'};

    private final OpusIdentification identification;
    private final VorbisComment comment;

    private OpusHeader(OpusIdentification identification, VorbisComment comment) {
        this.identification = Objects.requireNonNull(identification);
        this.comment = Objects.requireNonNull(comment);
    }

    static OpusHeader read(Packet first, PacketSource packets) throws IOException {
        ByteBuffer buffer = ByteBuffer.wrap(first.data());
        OpusIdentification identification = OpusIdentification.read(buffer);
        VorbisComment comment = readComment(packets);
        return new OpusHeader(identification, comment);
    }

    private static VorbisComment readComment(PacketSource packets) throws IOException {
        Packet packet = packets.next();
        if (packet == null) {
            throw new OpusException("Expected 'OpusTags' header");
        }

        ByteBuffer buffer = ByteBuffer.wrap(packet.data())
            .order(ByteOrder.LITTLE_ENDIAN);
        if (!IOUtils.matches(buffer, TAGS_MAGIC)) {
            throw new OpusException("Expected 'OpusTags' header");
        }

        try {
            return VorbisComment.read(buffer);
        } catch (BufferUnderflowException | IOException e) {
            // failed metadata shouldn't cause an exception
            return new VorbisComment("", Map.of());
        }
    }

    OpusIdentification identification() {
        return identification;
    }

    VorbisComment comment() {
        return comment;
    }

    @Override
    public String toString() {
        return "OpusHeader{" +
            "identification=" + identification + ", " +
            "comment=" + comment +
            "}";
    }
}
