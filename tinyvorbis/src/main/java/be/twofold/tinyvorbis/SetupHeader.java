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
        // Codebooks
        int codebookCount = bits.read(8) + 1;
        List<Codebook> codebooks = new ArrayList<>(codebookCount);
        for (int i = 0; i < codebookCount; i++) {
            codebooks.add(Codebook.read(bits));
        }

        // Time domain transforms
        int timeCount = bits.read(6) + 1;
        for (int i = 0; i < timeCount; i++) {
            if (bits.read(16) != 0) {
                throw new VorbisException("Invalid time domain transform");
            }
        }

        // Floors
        int floorCount = bits.read(6) + 1;
        List<Floor> floors = new ArrayList<>(floorCount);
        for (int i = 0; i < floorCount; i++) {
            floors.add(Floor.read(bits));
        }

        // Residues
        int residueCount = bits.read(6) + 1;
        List<Residue> residues = new ArrayList<>(residueCount);
        for (int i = 0; i < residueCount; i++) {
            residues.add(Residue.read(bits));
        }

        // Mappings
        int mappingCount = bits.read(6) + 1;
        List<Mapping> mappings = new ArrayList<>(mappingCount);
        for (int i = 0; i < mappingCount; i++) {
            mappings.add(Mapping.read(bits, audioChannels));
        }

        // Modes
        int modeCount = bits.read(6) + 1;
        List<Mode> modes = new ArrayList<>(modeCount);
        for (int i = 0; i < modeCount; i++) {
            modes.add(Mode.read(bits));
        }

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

    public void write(BitOutputStream bits, int audioChannels) throws IOException {
        // Codebooks
        bits.write(8, codebooks.size() - 1);
        for (Codebook codebook : codebooks) {
            codebook.write(bits);
        }

        // Time domain transform
        bits.write(6, 0);
        bits.write(16, 0);

        // Floors
        bits.write(6, floors.size() - 1);
        for (Floor floor : floors) {
            floor.write(bits);
        }

        // Residues
        bits.write(6, residues.size() - 1);
        for (Residue residue : residues) {
            residue.write(bits);
        }

        // Mappings
        bits.write(6, mappings.size() - 1);
        for (Mapping mapping : mappings) {
            mapping.write(bits, audioChannels);
        }

        // Modes
        bits.write(6, modes.size() - 1);
        for (Mode mode : modes) {
            mode.write(bits);
        }

        // Framing flag
        bits.write(1, 1);
    }
}
