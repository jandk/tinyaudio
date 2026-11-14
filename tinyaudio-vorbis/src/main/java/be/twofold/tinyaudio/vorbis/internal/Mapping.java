package be.twofold.tinyaudio.vorbis.internal;

import be.twofold.tinyaudio.core.internal.BitReader;
import be.twofold.tinyaudio.vorbis.VorbisException;

import java.io.IOException;

final class Mapping {
    private final int[] mux;
    private final int[] submapFloor;
    private final int[] submapResidue;
    private final int[] magnitude;
    private final int[] angle;

    Mapping(
        int[] mux,
        int[] submapFloor,
        int[] submapResidue,
        int[] magnitude,
        int[] angle
    ) {
        this.mux = mux;
        this.submapFloor = submapFloor;
        this.submapResidue = submapResidue;
        this.magnitude = magnitude;
        this.angle = angle;
    }

    static Mapping unpack(BitReader bits, int channels, int floorCount, int residueCount) throws IOException {
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

            int channelLog = Util.ilog(channels - 1);
            magnitude = new int[couplingSteps];
            angle = new int[couplingSteps];
            for (int i = 0; i < couplingSteps; i++) {
                int mag = bits.read(channelLog);
                int ang = bits.read(channelLog);
                if (mag == ang || mag >= channels || ang >= channels) {
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

        int[] mux = new int[channels];
        if (submaps > 1) {
            for (int i = 0; i < channels; i++) {
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
            mux,
            submapFloor,
            submapResidue,
            magnitude,
            angle
        );
    }

    int[] mux() {
        return mux;
    }

    int[] submapFloor() {
        return submapFloor;
    }

    int[] submapResidue() {
        return submapResidue;
    }

    int[] magnitude() {
        return magnitude;
    }

    int[] angle() {
        return angle;
    }

    int submaps() {
        return submapFloor.length;
    }

    int couplingSteps() {
        return magnitude.length;
    }
}
