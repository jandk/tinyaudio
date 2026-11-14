package be.twofold.tinyaudio.opus.celt;

import be.twofold.tinyaudio.core.internal.MDCT;

/**
 * This uses the same MDCT implementation as Vorbis uses.
 * <p>
 * Just need to do a little tweaking to make it work with Celt.
 */
public final class CeltMdct {
    // One transform per block size, indexed by shift: 1920, 960, 480 and 240 samples at 48 kHz
    private final MDCT[] mdct;
    private final float[] coeffs;
    private final float[] y;

    public CeltMdct() {
        CeltMode mode = CeltMode.mode48000_960_120;
        int n = mode.shortMdctSize << (mode.maxLM + 1);
        mdct = new MDCT[mode.maxLM + 1];
        for (int shift = 0; shift <= mode.maxLM; shift++) {
            mdct[shift] = new MDCT(n >> shift);
        }
        coeffs = new float[n >> 1];
        y = new float[n];
    }

    /**
     * Compute a backward MDCT (no scaling) and performs weighted overlap-add
     * (scales implicitly by 1/2).
     */
    void backward(float[] in, int inOff, float[] out, int outOff, float[] window, int overlap, int shift, int stride) {
        MDCT m = mdct[shift];
        int n2 = coeffs.length >> shift;
        int n4 = n2 >> 1;

        // IMDCT on the interleaved the sub-frames
        for (int k = 0; k < n2; k++) {
            coeffs[k] = in[inOff + k * stride];
        }
        m.backward(coeffs, 0, y, 0);

        // Mirror on both sides for TDAC
        int s = n4 - (overlap >> 1);
        for (int i = 0; i < overlap; i++) {
            out[outOff + i] += window[i] * y[s + i];
        }
        System.arraycopy(y, s + overlap, out, outOff + overlap, n2 - overlap);
        for (int i = 0; i < overlap; i++) {
            out[outOff + n2 + i] = window[overlap - 1 - i] * y[s + n2 + i];
        }
    }
}
