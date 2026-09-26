package be.twofold.tinyvorbis;

import java.io.IOException;

record Mapping(
    int submaps,
    int[] mux,
    int[] submapFloor,
    int[] submapResidue,
    int couplingSteps,
    int[] magnitude,
    int[] angle
) {
    static Mapping read(BitInputStream bits, int audioChannels, int floorCount, int residueCount) throws IOException {
        int type = bits.read(16);
        if (type != 0) {
            throw new VorbisException("Invalid mapping type: " + type);
        }

        int submaps = bits.readBool()
            ? bits.read(4) + 1
            : 1;

        int couplingSteps = 0;
        int[] magnitude = new int[0];
        int[] angle = new int[0];
        if (bits.readBool()) {
            couplingSteps = bits.read(8) + 1;

            int audioChannelLog = Util.ilog(audioChannels - 1);
            magnitude = new int[couplingSteps];
            angle = new int[couplingSteps];
            for (int i = 0; i < couplingSteps; i++) {
                int mag = bits.read(audioChannelLog);
                int ang = bits.read(audioChannelLog);
                if (mag == ang || mag >= audioChannels || ang >= audioChannels) {
                    throw new VorbisException("Invalid coupling step: magnitude=" + mag + ", angle=" + ang);
                }
                magnitude[i] = mag;
                angle[i] = ang;
            }
        }

        int reserved = bits.read(2);
        if (reserved != 0) { /* 2,3:reserved */
            throw new VorbisException("Invalid reserved value: " + reserved);
        }

        int[] mux = new int[audioChannels];
        if (submaps > 1) {
            for (int i = 0; i < audioChannels; i++) {
                int submap = bits.read(4);
                if (submap >= submaps) {
                    throw new VorbisException("Channel multiplex out of range: " + submap);
                }
                mux[i] = submap;
            }
        }

        int[] submapFloor = new int[submaps];
        int[] submapResidue = new int[submaps];
        for (int i = 0; i < submaps; i++) {
            bits.read(8); // unused time configuration placeholder
            int floor = bits.read(8);
            if (floor >= floorCount) {
                throw new VorbisException("Submap floor out of range: " + floor);
            }
            int residue = bits.read(8);
            if (residue >= residueCount) {
                throw new VorbisException("Submap residue out of range: " + residue);
            }
            submapFloor[i] = floor;
            submapResidue[i] = residue;
        }

        return new Mapping(
            submaps,
            mux,
            submapFloor,
            submapResidue,
            couplingSteps,
            magnitude,
            angle);
    }

    void write(BitOutputStream bits, int audioChannels) throws IOException {
        bits.write(16, 0);

        bits.writeBool(submaps > 1);
        if (submaps > 1) {
            bits.write(4, submaps - 1);
        }

        bits.writeBool(couplingSteps > 0);
        if (couplingSteps > 0) {
            int audioChannelLog = Util.ilog(audioChannels - 1);
            bits.write(8, couplingSteps - 1);
            for (int i = 0; i < couplingSteps; i++) {
                bits.write(audioChannelLog, magnitude[i]);
                bits.write(audioChannelLog, angle[i]);
            }
        }

        bits.write(2, 0); // reserved

        if (submaps > 1) {
            for (int i = 0; i < audioChannels; i++) {
                bits.write(4, mux[i]);
            }
        }

        for (int i = 0; i < submaps; i++) {
            bits.write(8, 0);
            bits.write(8, submapFloor[i]);
            bits.write(8, submapResidue[i]);
        }
    }
}
