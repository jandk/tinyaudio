package be.twofold.tinyaudio.vorbis.internal;

import be.twofold.tinyaudio.core.internal.BitReader;
import be.twofold.tinyaudio.vorbis.mdct.MDCT;

import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Objects;

public final class VorbisDecoder {
    private final Identification identification;
    private final Setup setup;
    private final float[][] spectrum;

    // Floor
    private final float[][] floorY;
    private final boolean[] floorUnused;

    // Residue
    private final boolean[] noResidue;
    private final float[][] pcmBundle;
    private final boolean[] zeroBundle;
    private final MDCT mdct0;
    private final MDCT mdct1;

    // Windowing
    private final float[][] overlap;
    private final float[] window0;
    private final float[] window1;

    private int previousBlockSize;

    public VorbisDecoder(Identification identification, Setup setup) {
        this.identification = Objects.requireNonNull(identification);
        this.setup = Objects.requireNonNull(setup);

        spectrum = new float[identification.channels()][identification.blockSize1()];

        floorY = new float[identification.channels()][256];
        floorUnused = new boolean[identification.channels()];
        noResidue = new boolean[identification.channels()];
        pcmBundle = new float[identification.channels()][];
        zeroBundle = new boolean[identification.channels()];
        mdct0 = new MDCT(identification.blockSize0());
        mdct1 = new MDCT(identification.blockSize1());

        overlap = new float[identification.channels()][identification.blockSize1() / 2];
        window0 = slope(identification.blockSize0() / 2);
        window1 = slope(identification.blockSize1() / 2);
    }

    private float[] slope(int n) {
        float[] w = new float[n];
        for (int i = 0; i < n; i++) {
            double s = Math.sin((i + 0.5) / n * (Math.PI / 2));
            w[i] = (float) Math.sin((Math.PI / 2) * s * s);
        }
        return w;
    }

    /**
     * Decodes one audio packet into planar float samples in {@code [-1, 1]}. Returns the number of samples per channel,
     * which is 0 for the first audio packet and for packets that aren't audio.
     *
     * @param pcm {@code [channels][blockSize1 / 2]}
     */
    public int decodeBlock(byte[] data, int off, int len, float[][] pcm) {
        if (previousBlockSize != 0) {
            for (int ch = 0; ch < identification.channels(); ch++) {
                System.arraycopy(spectrum[ch], previousBlockSize / 2, overlap[ch], 0, previousBlockSize / 2);
            }
        }

        var bits = new BitReader(data, off, len, ByteOrder.LITTLE_ENDIAN);
        if (bits.readBool()) { // packet type
            return 0;
        }

        int modeCountLog2 = Util.ilog(setup.modes().size() - 1);
        int modeNumber = bits.read(modeCountLog2);
        if (modeNumber >= setup.modes().size()) {
            return 0;
        }
        Mode mode = setup.modes().get(modeNumber);

        int n;
        if (mode.blockFlag()) {
            n = identification.blockSize1();
            // Previous and next window flags. Like libvorbis, the window follows
            // the actual neighbouring block sizes, so the values aren't needed.
            bits.skip(2);
        } else {
            n = identification.blockSize0();
        }
        if (bits.isOverrun()) {
            return 0;
        }

        // 4.3.2 floor curve decode
        Mapping mapping = setup.mappings().get(mode.mapping());
        for (int ch = 0; ch < identification.channels(); ch++) {
            int submap = mapping.mux()[ch];
            Floor floor = setup.floors().get(mapping.submapFloor()[submap]);
            floorUnused[ch] = floor.decode(bits, setup.codebooks(), floorY[ch]);
        }

        // 4.3.3 nonzero vector propagate
        System.arraycopy(floorUnused, 0, noResidue, 0, identification.channels());
        for (int i = 0; i < mapping.couplingSteps(); i++) {
            int mag = mapping.magnitude()[i];
            int ang = mapping.angle()[i];
            if (!noResidue[mag] || !noResidue[ang]) {
                noResidue[mag] = noResidue[ang] = false;
            }
        }

        // 4.3.4 residue decode
        for (int i = 0; i < mapping.submaps(); i++) {
            int count = 0;
            for (int j = 0; j < identification.channels(); j++) {
                if (mapping.mux()[j] == i) {
                    pcmBundle[count] = spectrum[j];
                    zeroBundle[count] = noResidue[j];
                    count++;
                }
            }

            Residue residue = setup.residues().get(mapping.submapResidue()[i]);
            residue.decode(bits, setup.codebooks(), zeroBundle, pcmBundle, count, n / 2);
        }

        // 4.3.5 inverse coupling
        for (int i = mapping.couplingSteps() - 1; i >= 0; i--) {
            float[] pcmMag = spectrum[mapping.magnitude()[i]];
            float[] pcmAng = spectrum[mapping.angle()[i]];

            for (int j = 0; j < n / 2; j++) {
                float mag = pcmMag[j];
                float ang = pcmAng[j];

                if (mag > 0) {
                    if (ang > 0) {
                        pcmAng[j] = mag - ang;
                    } else {
                        pcmAng[j] = mag;
                        pcmMag[j] = mag + ang;
                    }
                } else {
                    if (ang > 0) {
                        pcmAng[j] = mag + ang;
                    } else {
                        pcmAng[j] = mag;
                        pcmMag[j] = mag - ang;
                    }
                }
            }
        }

        // 4.3.6 dot product
        for (int ch = 0; ch < identification.channels(); ch++) {
            if (floorUnused[ch]) {
                Arrays.fill(spectrum[ch], 0, n / 2, 0.0f);
            } else {
                int submap = mapping.mux()[ch];
                Floor floor = setup.floors().get(mapping.submapFloor()[submap]);
                floor.synthesize(floorY[ch], spectrum[ch], n / 2);
            }
        }

        // 4.3.7 inverse mdct
        MDCT mdct = mode.blockFlag() ? mdct1 : mdct0;
        for (int ch = 0; ch < identification.channels(); ch++) {
            mdct.backward(spectrum[ch], 0);
        }

        // 4.3.8 overlap add, in place in the overlap buffer. The first packet only primes it.
        int frames = 0;
        if (previousBlockSize != 0) {
            for (int ch = 0; ch < identification.channels(); ch++) {
                overlapAdd(overlap[ch], spectrum[ch], previousBlockSize, n);
            }
            frames = previousBlockSize / 4 + n / 4;
            for (int ch = 0; ch < identification.channels(); ch++) {
                System.arraycopy(overlap[ch], 0, pcm[ch], 0, frames);
            }
        }

        previousBlockSize = n;
        return frames;
    }

    private void overlapAdd(float[] previous, float[] current, int previousN, int currentN) {
        int slope = Math.min(previousN, currentN) / 2;
        float[] window = slope == identification.blockSize0() / 2 ? window0 : window1;
        int o = previousN / 4 - slope / 2; // [0, o) is the previous block's flat part, already in place
        int currentStart = currentN / 4 - slope / 2;

        for (int i = 0; i < slope; i++, o++) {
            previous[o] = previous[o] * window[slope - 1 - i] + current[currentStart + i] * window[i];
        }
        for (int i = currentStart + slope; i < currentN / 2; i++) {
            previous[o++] = current[i];
        }
    }
}
