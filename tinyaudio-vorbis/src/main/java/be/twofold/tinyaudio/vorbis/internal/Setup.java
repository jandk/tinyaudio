package be.twofold.tinyaudio.vorbis.internal;

import be.twofold.tinyaudio.core.internal.BitReader;
import be.twofold.tinyaudio.vorbis.VorbisException;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

public final class Setup {
    private final List<Codebook> codebooks;
    private final List<Floor> floors;
    private final List<Residue> residues;
    private final List<Mapping> mappings;
    private final List<Mode> modes;

    Setup(List<Codebook> codebooks, List<Floor> floors, List<Residue> residues, List<Mapping> mappings, List<Mode> modes) {
        this.codebooks = List.copyOf(codebooks);
        this.floors = List.copyOf(floors);
        this.residues = List.copyOf(residues);
        this.mappings = List.copyOf(mappings);
        this.modes = List.copyOf(modes);
    }

    public static Setup read(ByteBuffer buffer, int audioChannels) throws IOException {
        BitReader bits = new BitReader(
            buffer.array(),
            buffer.arrayOffset() + buffer.position(),
            buffer.remaining(),
            ByteOrder.LITTLE_ENDIAN
        );
        return read(bits, audioChannels);
    }

    static Setup read(BitReader bits, int audioChannels) throws IOException {
        List<Codebook> codebooks = readList(bits, 8, Codebook::unpack);
        readList(bits, 6, Setup::unpackTimeValue);
        List<Floor> floors = readList(bits, 6, in -> Floor.unpack(in, codebooks.size()));
        List<Residue> residues = readList(bits, 6, in -> Residue.unpack(in, codebooks));
        List<Mapping> mappings = readList(bits, 6, in -> Mapping.unpack(in, audioChannels, floors.size(), residues.size()));
        List<Mode> modes = readList(bits, 6, in -> Mode.unpack(in, mappings.size()));

        if (!bits.readBool()) { // framing flag
            throw new VorbisException("Invalid framing flag");
        }

        return new Setup(
            codebooks,
            floors,
            residues,
            mappings,
            modes
        );
    }

    private static <T> List<T> readList(BitReader bits, int countBits, Reader<T> reader) throws IOException {
        int count = bits.read(countBits) + 1;
        List<T> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            list.add(reader.read(bits));
        }
        return list;
    }

    private static Void unpackTimeValue(BitReader bits) throws IOException {
        if (bits.read(16) != 0) {
            throw new VorbisException("Invalid time domain transform");
        }
        return null;
    }

    List<Codebook> codebooks() {
        return codebooks;
    }

    List<Floor> floors() {
        return floors;
    }

    List<Residue> residues() {
        return residues;
    }

    List<Mapping> mappings() {
        return mappings;
    }

    List<Mode> modes() {
        return modes;
    }

    private interface Reader<T> {
        T read(BitReader bits) throws IOException;
    }
}
