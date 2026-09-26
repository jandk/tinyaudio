package be.twofold.tinyvorbis;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public record SetupHeader(
    List<Codebook> codebooks,
    List<Floor> floors,
    List<Residue> residues,
    List<Mapping> mappings,
    List<Mode> modes
) {
    public SetupHeader {
        codebooks = List.copyOf(codebooks);
        floors = List.copyOf(floors);
        residues = List.copyOf(residues);
        mappings = List.copyOf(mappings);
        modes = List.copyOf(modes);
    }

    public static SetupHeader read(BitInputStream bits, int audioChannels) throws IOException {
        List<Codebook> codebooks = readList(bits, 8, Codebook::read);
        readList(bits, 6, SetupHeader::readTimeValue);
        List<Floor> floors = readList(bits, 6, in -> Floor.read(in, codebooks.size()));
        List<Residue> residues = readList(bits, 6, in -> Residue.read(in, codebooks));
        List<Mapping> mappings = readList(bits, 6, in -> Mapping.read(in, audioChannels, floors.size(), residues.size()));
        List<Mode> modes = readList(bits, 6, in -> Mode.read(in, mappings.size()));

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

    private static <T> List<T> readList(BitInputStream bits, int countBits, Reader<T> reader) throws IOException {
        int count = bits.read(countBits) + 1;
        List<T> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            list.add(reader.read(bits));
        }
        return list;
    }

    private static Void readTimeValue(BitInputStream in) throws IOException {
        if (in.read(16) != 0) {
            throw new VorbisException("Invalid time domain transform");
        }
        return null;
    }

    public void write(BitOutputStream bits, int audioChannels) throws IOException {
        writeList(bits, 8, codebooks, Codebook::write);
        writeList(bits, 6, List.of(0), (value, out) -> out.write(16, value));
        writeList(bits, 6, floors, Floor::write);
        writeList(bits, 6, residues, Residue::write);
        writeList(bits, 6, mappings, (mapping, out) -> mapping.write(out, audioChannels));
        writeList(bits, 6, modes, Mode::write);

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
        T read(BitInputStream bits) throws IOException;
    }

    private interface Writer<T> {
        void write(T value, BitOutputStream bits) throws IOException;
    }
}
