package be.twofold.tinyaudio.vorbis;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

final class SetupHeader {
    private final List<Codebook> codebooks;
    private final List<Floor> floors;
    private final List<Residue> residues;
    private final List<Mapping> mappings;
    private final List<Mode> modes;

    SetupHeader(List<Codebook> codebooks, List<Floor> floors, List<Residue> residues, List<Mapping> mappings, List<Mode> modes) {
        codebooks = List.copyOf(codebooks);
        floors = List.copyOf(floors);
        residues = List.copyOf(residues);
        mappings = List.copyOf(mappings);
        modes = List.copyOf(modes);
        this.codebooks = codebooks;
        this.floors = floors;
        this.residues = residues;
        this.mappings = mappings;
        this.modes = modes;
    }

    static SetupHeader read(BitReader bits, int audioChannels) throws IOException {
        List<Codebook> codebooks = readList(bits, 8, Codebook::unpack);
        readList(bits, 6, SetupHeader::unpackTimeValue);
        List<Floor> floors = readList(bits, 6, in -> Floor.unpack(in, codebooks.size()));
        List<Residue> residues = readList(bits, 6, in -> Residue.unpack(in, codebooks));
        List<Mapping> mappings = readList(bits, 6, in -> Mapping.unpack(in, audioChannels, floors.size(), residues.size()));
        List<Mode> modes = readList(bits, 6, in -> Mode.unpack(in, mappings.size()));

        int framingFlag = bits.read(1);
        if (framingFlag != 1) {
            throw new VorbisException("Invalid framing flag: " + framingFlag);
        }

        return new SetupHeader(
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

    void write(BitOutputStream bits, int audioChannels) throws IOException {
        writeList(bits, 8, codebooks, Codebook::pack);
        writeList(bits, 6, List.of(0), (value, out) -> out.write(16, value));
        writeList(bits, 6, floors, Floor::pack);
        writeList(bits, 6, residues, Residue::pack);
        writeList(bits, 6, mappings, (mapping, out) -> mapping.pack(out, audioChannels));
        writeList(bits, 6, modes, Mode::pack);

        // Framing flag
        bits.write(1, 1);
    }

    private <T> void writeList(BitOutputStream bits, int countBits, List<T> list, Writer<T> writer) throws IOException {
        if (list.isEmpty() || list.size() > 1 << countBits) {
            throw new IllegalArgumentException("Too many elements");
        }
        bits.write(countBits, list.size() - 1);
        for (T value : list) {
            writer.write(value, bits);
        }
    }

    private interface Reader<T> {
        T read(BitReader bits) throws IOException;
    }

    private interface Writer<T> {
        void write(T value, BitOutputStream bits) throws IOException;
    }
}
