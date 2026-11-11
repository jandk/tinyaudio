package be.twofold.tinyaudio.opus.celt;

import java.util.Arrays;
import java.util.Objects;

import static be.twofold.tinyaudio.opus.celt.RangeDecoder.BITRES;
import static be.twofold.tinyaudio.opus.celt.Utils.celt_assert;

public final class CeltDecoder {
    private static final int MAX_PERIOD = 1024;
    private static final int DECODE_BUFFER_SIZE = 2048;
    private static final int COMBFILTER_MINPERIOD = 15;
    private static final float CELT_SIG_SCALE = 32768.0f;
    private static final float VERY_SMALL = 1e-30f;
    private static final byte[] trim_icdf = {126, 124, 119, 109, 87, 41, 19, 9, 4, 2, 0};
    private static final byte[] spread_icdf = {25, 23, 2, 0};
    private static final byte[] tapset_icdf = {2, 1, 0};
    private static final byte[][] tf_select_table = {
        {0, -1, 0, -1, 0, -1, 0, -1},
        {0, -1, 0, -2, 1, +0, 1, -1},
        {0, -2, 0, -3, 2, +0, 1, -1},
        {0, -2, 0, -3, 3, +0, 1, -1},
    };
    private static final float[][] gains = {
        {0.3066406250f, 0.2170410156f, 0.1296386719f},
        {0.4638671875f, 0.2680664062f, 00000000000.f},
        {0.7998046875f, 0.1000976562f, 00000000000.f}
    };

    private final CeltMode mode;
    private final CeltMdct mdct;
    private final int channels;
    private final int downsample;
    private final float[] preemph_memD = new float[2];
    private final float[] _decode_mem; // Size = channels*(DECODE_BUFFER_SIZE+mode->overlap)
    private final float[] oldEBands;
    private final float[] oldLogE;
    private final float[] oldLogE2;

    private int postfilter_period;
    private int postfilter_period_old;
    private float postfilter_gain;
    private float postfilter_gain_old;
    private int postfilter_tapset;
    private int postfilter_tapset_old;

    private int rng;

    public CeltDecoder(CeltMode mode, int channels) {
        this.mode = Objects.requireNonNull(mode);
        this.mdct = new CeltMdct(mode);
        this.channels = channels;
        this.downsample = resampling_factor(mode.Fs);
        this._decode_mem = new float[channels * (DECODE_BUFFER_SIZE + mode.overlap)];
        this.oldEBands = new float[2 * mode.nbEBands];
        this.oldLogE = new float[2 * mode.nbEBands];
        this.oldLogE2 = new float[2 * mode.nbEBands];
        Arrays.fill(oldLogE, -28.f);
        Arrays.fill(oldLogE2, -28.f);
    }

    private int resampling_factor(int rate) {
        switch (rate) {
            case 48000:
                return 1;
            case 24000:
                return 2;
            case 16000:
                return 3;
            case 12000:
                return 4;
            case 8000:
                return 6;
            default:
                celt_assert(false);
                return 0;
        }
    }

    public int celt_decode_with_ec(byte[] data, int dataOff, int len, float[] pcm, int pcmOff, int frame_size, int C, int end) {
        RangeDecoder dec = new RangeDecoder(data, dataOff, len);
        int LM;
        for (LM = 0; LM <= mode.maxLM; LM++) {
            if (mode.shortMdctSize << LM == frame_size) {
                break;
            }
        }
        celt_assert(LM <= mode.maxLM);

        if (C == 1) {
            for (int i = 0; i < mode.nbEBands; i++) {
                oldEBands[i] = Math.max(oldEBands[i], oldEBands[mode.nbEBands + i]);
            }
        }

        int total_bits = len * 8;
        int tell = dec.ec_tell();

        int silence;
        if (tell >= total_bits) {
            silence = 1;
        } else if (tell == 1) {
            silence = dec.ec_dec_bit_logp(15);
        } else {
            silence = 0;
        }
        if (silence != 0) {
            // Pretend we've read all the remaining bits
            tell = len * 8;
            dec.nbits_total += tell - dec.ec_tell();
        }

        float postfilter_gain = 0;
        int postfilter_pitch = 0;
        int postfilter_tapset = 0;
        if (tell + 16 <= total_bits) {
            if (dec.ec_dec_bit_logp(1) != 0) {
                int octave = dec.ec_dec_uint(6);
                postfilter_pitch = (16 << octave) + dec.ec_dec_bits(4 + octave) - 1;
                int qg = dec.ec_dec_bits(3);
                if (dec.ec_tell() + 2 <= total_bits) {
                    postfilter_tapset = dec.ec_dec_icdf(tapset_icdf, 2);
                }
                postfilter_gain = 0.09375f * (qg + 1);
            }
            tell = dec.ec_tell();
        }

        int isTransient;
        if (LM > 0 && tell + 3 <= total_bits) {
            isTransient = dec.ec_dec_bit_logp(3);
            tell = dec.ec_tell();
        } else {
            isTransient = 0;
        }

        int shortBlocks = isTransient != 0 ? 1 << LM : 0;

        // Decode the global flags (first symbols in the stream)
        int intra_ener = tell + 3 <= total_bits ? dec.ec_dec_bit_logp(3) : 0;
        // Get band energies
        QuantBands.unquant_coarse_energy(mode, end, oldEBands, intra_ener, dec, C, LM);

        int[] tf_res = new int[mode.nbEBands];
        tf_decode(end, isTransient, tf_res, LM, dec);

        tell = dec.ec_tell();
        int spread_decision = Bands.SPREAD_NORMAL;
        if (tell + 4 <= total_bits) {
            spread_decision = dec.ec_dec_icdf(spread_icdf, 5);
        }

        int[] pulses = new int[mode.nbEBands];
        int[] cap = new int[mode.nbEBands];
        int[] offsets = new int[mode.nbEBands];
        int[] fine_priority = new int[mode.nbEBands];

        init_caps(mode, cap, LM, C);

        int dynalloc_logp = 6;
        total_bits <<= BITRES;
        tell = dec.ec_tell_frac();
        for (int i = 0; i < end; i++) {
            int width = C * (mode.eBands[i + 1] - mode.eBands[i]) << LM;
            // quanta is 6 bits, but no more than 1 bit/sample
            // and no less than 1/8 bit/sample
            int quanta = Math.min(width << BITRES, Math.max(6 << BITRES, width));
            int dynalloc_loop_logp = dynalloc_logp;
            int boost = 0;
            while (tell + (dynalloc_loop_logp << BITRES) < total_bits && boost < cap[i]) {
                int flag = dec.ec_dec_bit_logp(dynalloc_loop_logp);
                tell = dec.ec_tell_frac();
                if (flag == 0) {
                    break;
                }
                boost += quanta;
                total_bits -= quanta;
                dynalloc_loop_logp = 1;
            }
            offsets[i] = boost;
            // Making dynalloc more likely
            if (boost > 0) {
                dynalloc_logp = Math.max(2, dynalloc_logp - 1);
            }
        }

        int[] fine_quant = new int[mode.nbEBands];
        int alloc_trim = tell + (6 << BITRES) <= total_bits ? dec.ec_dec_icdf(trim_icdf, 7) : 5;

        int bits = ((len * 8) << BITRES) - dec.ec_tell_frac() - 1;
        int anti_collapse_rsv = isTransient != 0 && LM >= 2 && bits >= ((LM + 2) << BITRES) ? (1 << BITRES) : 0;
        bits -= anti_collapse_rsv;

        int[] intensity = new int[1];
        int[] dual_stereo = new int[1];
        int[] balance = new int[1];
        int codedBands = Rate.compute_allocation(mode, end, offsets, cap,
            alloc_trim, intensity, dual_stereo, bits, balance, pulses,
            fine_quant, fine_priority, C, LM, dec);

        QuantBands.unquant_fine_energy(mode, end, oldEBands, fine_quant, dec, C);

        // Decode fixed codebook
        int N = mode.shortMdctSize << LM;
        float[] X = new float[C * N]; // Interleaved normalised MDCTs
        int[] collapse_masks = new int[C * mode.nbEBands];
        rng = Bands.quant_all_bands(mode, end, X, C == 2 ? X : null, N, collapse_masks,
            pulses, shortBlocks, spread_decision, dual_stereo[0] != 0, intensity[0], tf_res,
            len * (8 << BITRES) - anti_collapse_rsv, balance[0], dec, LM, codedBands, rng,
            channels == 1);

        boolean anti_collapse_on = anti_collapse_rsv > 0 && dec.ec_dec_bits(1) != 0;

        QuantBands.unquant_energy_finalise(mode, end, oldEBands, fine_quant, fine_priority, len * 8 - dec.ec_tell(), dec, C);

        if (anti_collapse_on) {
            Bands.anti_collapse(mode, X, collapse_masks, LM, C, N, end, oldEBands, oldLogE, oldLogE2, pulses, rng);
        }

        int CC = channels;
        int[] decode_memOff = new int[CC];
        int[] out_memOff = new int[CC];
        int[] overlap_memOff = new int[CC];
        for (int c = 0; c < CC; c++) {
            decode_memOff[c] = c * (DECODE_BUFFER_SIZE + mode.overlap);
            out_memOff[c] = decode_memOff[c] + DECODE_BUFFER_SIZE - MAX_PERIOD;
            overlap_memOff[c] = decode_memOff[c] + DECODE_BUFFER_SIZE;
        }

        int[] out_synOff = new int[CC];
        for (int c = 0; c < CC; c++) {
            System.arraycopy(_decode_mem, decode_memOff[c] + N, _decode_mem, decode_memOff[c], DECODE_BUFFER_SIZE - N);
            out_synOff[c] = out_memOff[c] + MAX_PERIOD - N;
        }

        int effEnd = Math.min(end, mode.effEBands);
        celt_synthesis(X, _decode_mem, out_synOff, overlap_memOff, oldEBands, effEnd, C, CC, shortBlocks, LM, downsample, silence);

        if (silence != 0) {
            for (int i = 0; i < C * mode.nbEBands; i++) {
                oldEBands[i] = -28.f;
            }
        }

        for (int c = 0; c < CC; c++) {
            this.postfilter_period = Math.max(this.postfilter_period, COMBFILTER_MINPERIOD);
            this.postfilter_period_old = Math.max(this.postfilter_period_old, COMBFILTER_MINPERIOD);
            comb_filter(_decode_mem, out_synOff[c], _decode_mem, out_synOff[c],
                this.postfilter_period_old, this.postfilter_period, mode.shortMdctSize,
                this.postfilter_gain_old, this.postfilter_gain, this.postfilter_tapset_old, this.postfilter_tapset,
                mode.window, mode.overlap);
            if (LM != 0) {
                comb_filter(_decode_mem, out_synOff[c] + mode.shortMdctSize, _decode_mem, out_synOff[c] + mode.shortMdctSize,
                    this.postfilter_period, postfilter_pitch, N - mode.shortMdctSize,
                    this.postfilter_gain, postfilter_gain, this.postfilter_tapset, postfilter_tapset,
                    mode.window, mode.overlap);
            }
        }
        this.postfilter_period_old = this.postfilter_period;
        this.postfilter_gain_old = this.postfilter_gain;
        this.postfilter_tapset_old = this.postfilter_tapset;
        this.postfilter_period = postfilter_pitch;
        this.postfilter_gain = postfilter_gain;
        this.postfilter_tapset = postfilter_tapset;
        if (LM != 0) {
            this.postfilter_period_old = this.postfilter_period;
            this.postfilter_gain_old = this.postfilter_gain;
            this.postfilter_tapset_old = this.postfilter_tapset;
        }

        if (C == 1) {
            for (int i = 0; i < mode.nbEBands; i++) {
                oldEBands[mode.nbEBands + i] = oldEBands[i];
            }
        }

        // In case start or end were to change
        if (isTransient == 0) {
            System.arraycopy(oldLogE, 0, oldLogE2, 0, 2 * mode.nbEBands);
            System.arraycopy(oldEBands, 0, oldLogE, 0, 2 * mode.nbEBands);
        } else {
            for (int i = 0; i < 2 * mode.nbEBands; i++) {
                oldLogE[i] = Math.min(oldLogE[i], oldEBands[i]);
            }
        }
        for (int c = 0; c < 2; c++) {
            for (int i = end; i < mode.nbEBands; i++) {
                oldEBands[c * mode.nbEBands + i] = 0;
                oldLogE[c * mode.nbEBands + i] = oldLogE2[c * mode.nbEBands + i] = -28.f;
            }
        }

        rng = dec.rng();

        deemphasis(_decode_mem, out_synOff, pcm, pcmOff, N, CC, downsample, mode.preemph, preemph_memD);
        celt_assert(dec.ec_tell() <= 8 * len);
        return frame_size / downsample;
    }

    private void tf_decode(int end, int isTransient, int[] tf_res, int LM, RangeDecoder dec) {
        int budget = dec.storage * 8;
        int tell = dec.ec_tell();
        int logp = isTransient != 0 ? 2 : 4;
        int tf_select_rsv = LM > 0 && tell + logp + 1 <= budget ? 1 : 0;
        budget -= tf_select_rsv;
        int tf_changed = 0;
        int curr = 0;
        for (int i = 0; i < end; i++) {
            if (tell + logp <= budget) {
                curr ^= dec.ec_dec_bit_logp(logp);
                tell = dec.ec_tell();
                tf_changed |= curr;
            }
            tf_res[i] = curr;
            logp = isTransient != 0 ? 4 : 5;
        }
        int tf_select = 0;
        if (tf_select_rsv != 0 && tf_select_table[LM][4 * isTransient + tf_changed] != tf_select_table[LM][4 * isTransient + 2 + tf_changed]) {
            tf_select = dec.ec_dec_bit_logp(1);
        }
        for (int i = 0; i < end; i++) {
            tf_res[i] = tf_select_table[LM][4 * isTransient + 2 * tf_select + tf_res[i]];
        }
    }

    private void init_caps(CeltMode m, int[] cap, int LM, int C) {
        for (int i = 0; i < m.nbEBands; i++) {
            int N = (m.eBands[i + 1] - m.eBands[i]) << LM;
            cap[i] = (m.cacheCaps[m.nbEBands * (2 * LM + C - 1) + i] + 64) * C * N >> 2;
        }
    }

    /**
     * Turns the normalised spectrum X of C coded channels into CC output channels of time-domain signal (upmixing or
     * downmixing as needed).
     */
    private void celt_synthesis(float[] X, float[] out_mem, int[] out_synOff, int[] overlap_memOff, float[] oldBandE,
                                int end, int C, int CC, int shortBlocks, int LM, int downsample, int silence) {
        int M = 1 << LM;
        int N = mode.shortMdctSize << LM;
        float[] freq = new float[CC * N];

        if (CC == 2 && C == 1) {
            // Copy mono to stereo
            Bands.denormalise_bands(mode, X, 0, freq, 0, oldBandE, 0, end, M, downsample, silence);
            System.arraycopy(freq, 0, freq, N, N);
        } else if (CC == 1 && C == 2) {
            // Downmix stereo to mono
            float[] freq2 = new float[N];
            Bands.denormalise_bands(mode, X, 0, freq, 0, oldBandE, 0, end, M, downsample, silence);
            Bands.denormalise_bands(mode, X, N, freq2, 0, oldBandE, mode.nbEBands, end, M, downsample, silence);
            for (int i = 0; i < N; i++) {
                freq[i] = .5f * (freq[i] + freq2[i]);
            }
        } else {
            for (int c = 0; c < CC; c++) {
                Bands.denormalise_bands(mode, X, c * N, freq, c * N, oldBandE, c * mode.nbEBands, end, M, downsample,
                    silence);
            }
        }
        compute_inv_mdcts(shortBlocks, freq, out_mem, out_synOff, overlap_memOff, CC, LM);
    }

    private void comb_filter(float[] y, int yOff, float[] x, int xOff, int T0, int T1, int N, float g0, float g1,
                             int tapset0, int tapset1, float[] window, int overlap) {
        if (g0 == 0 && g1 == 0) {
            if (x != y || xOff != yOff) {
                System.arraycopy(x, xOff, y, yOff, N);
            }
            return;
        }
        // When the gain is zero, the period may be too; keep the taps inside the history
        T0 = Math.max(T0, COMBFILTER_MINPERIOD);
        T1 = Math.max(T1, COMBFILTER_MINPERIOD);
        float g00 = g0 * gains[tapset0][0];
        float g01 = g0 * gains[tapset0][1];
        float g02 = g0 * gains[tapset0][2];
        float g10 = g1 * gains[tapset1][0];
        float g11 = g1 * gains[tapset1][1];
        float g12 = g1 * gains[tapset1][2];
        // If the filter didn't change, we don't need the cross-fade
        if (g0 == g1 && T0 == T1 && tapset0 == tapset1) {
            overlap = 0;
        }
        for (int i = 0; i < overlap; i++) {
            float f = window[i] * window[i];
            int j = xOff + i;
            y[yOff + i] = x[j]
                + (1.0f - f) * g00 * (x[j - T0])
                + (1.0f - f) * g01 * (x[j - T0 + 1] + x[j - T0 - 1])
                + (1.0f - f) * g02 * (x[j - T0 + 2] + x[j - T0 - 2])
                + f * g10 * (x[j - T1])
                + f * g11 * (x[j - T1 + 1] + x[j - T1 - 1])
                + f * g12 * (x[j - T1 + 2] + x[j - T1 - 2]);
        }
        if (g1 == 0) {
            if (x != y || xOff != yOff) {
                System.arraycopy(x, xOff + overlap, y, yOff + overlap, N - overlap);
            }
            return;
        }
        for (int i = overlap; i < N; i++) {
            int j = xOff + i;
            y[yOff + i] = x[j] + g10 * x[j - T1] + g11 * (x[j - T1 + 1] + x[j - T1 - 1]) + g12 * (x[j - T1 + 2] + x[j - T1 - 2]);
        }
    }

    private void deemphasis(float[] in, int[] inOff, float[] pcm, int pcmOff, int N, int C, int downsample,
                            float[] coef, float[] mem) {
        int count = 0;
        for (int c = 0; c < C; c++) {
            float m = mem[c];
            int x = inOff[c];
            int y = pcmOff + c;
            for (int j = 0; j < N; j++) {
                // VERY_SMALL keeps the filter memory out of denormals in silence
                float tmp = in[x] + VERY_SMALL + m;
                m = coef[0] * tmp - coef[1] * in[x];
                tmp = coef[3] * tmp;
                x++;
                // Technically the store could be moved outside of the if because
                // the stores we don't want will just be overwritten
                if (count == 0) {
                    pcm[y] = SCALEOUT(tmp);
                }
                if (++count == downsample) {
                    y += C;
                    count = 0;
                }
            }
            mem[c] = m;
        }
    }

    /**
     * Compute the IMDCT and apply window for all sub-frames and
     * all channels in a frame.
     */
    private void compute_inv_mdcts(
        int shortBlocks, float[] X, float[] out_mem, int[] out_memOff,
        int[] overlap_memOff, int C, int LM
    ) {
        int N = mode.shortMdctSize << LM;
        int overlap = mode.overlap;
        float[] x = new float[N + overlap];

        for (int c = 0; c < C; c++) {
            int N2 = N;
            int B = 1;
            if (shortBlocks != 0) {
                N2 = mode.shortMdctSize;
                B = shortBlocks;
            }

            // Prevents problems from the imdct doing the overlap-add
            Arrays.fill(x, 0, overlap, 0.0f);

            for (int b = 0; b < B; b++) {
                // IMDCT on the interleaved the sub-frames
                mdct.backward(X, b + c * N2 * B, x, N2 * b, mode.window, overlap,
                    shortBlocks != 0 ? mode.maxLM : mode.maxLM - LM, B);
            }

            int out_memOffC = out_memOff[c];
            int overlap_memOffC = overlap_memOff[c];
            for (int j = 0; j < overlap; j++) {
                out_mem[out_memOffC + j] = x[j] + out_mem[overlap_memOffC + j];
            }
            System.arraycopy(x, overlap, out_mem, out_memOffC + overlap, N - overlap);
            System.arraycopy(x, N, out_mem, overlap_memOffC, overlap);
        }
    }

    private float SCALEOUT(float a) {
        return a * (1 / CELT_SIG_SCALE);
    }

    public int rng() {
        return rng;
    }
}
