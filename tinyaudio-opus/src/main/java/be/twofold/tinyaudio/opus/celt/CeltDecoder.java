package be.twofold.tinyaudio.opus.celt;

import java.util.Objects;

import static be.twofold.tinyaudio.opus.celt.RangeDecoder.BITRES;
import static be.twofold.tinyaudio.opus.celt.Utils.celt_assert;

public final class CeltDecoder {
    private static final byte[] trim_icdf = {126, 124, 119, 109, 87, 41, 19, 9, 4, 2, 0};
    private static final byte[] spread_icdf = {25, 23, 2, 0};
    private static final byte[] tapset_icdf = {2, 1, 0};
    private static final byte[][] tf_select_table = {
        {0, -1, 0, -1, 0, -1, 0, -1},
        {0, -1, 0, -2, 1, +0, 1, -1},
        {0, -2, 0, -3, 2, +0, 1, -1},
        {0, -2, 0, -3, 3, +0, 1, -1},
    };

    private final CeltMode mode;
    private int rng;

    private final float[] oldBandE;

    public CeltDecoder(CeltMode mode, int channels) {
        this.mode = Objects.requireNonNull(mode);
        this.oldBandE = new float[2 * mode.nbEBands];
    }

    public int rng() {
        return rng;
    }

    static void tf_decode(int end, int isTransient, int[] tf_res, int LM, RangeDecoder dec) {
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
        if (tf_select_rsv != 0 &&
            tf_select_table[LM][4 * isTransient + tf_changed] != tf_select_table[LM][4 * isTransient + 2 + tf_changed]) {
            tf_select = dec.ec_dec_bit_logp(1);
        }
        for (int i = 0; i < end; i++) {
            tf_res[i] = tf_select_table[LM][4 * isTransient + 2 * tf_select + tf_res[i]];
        }
    }

    static void init_caps(CeltMode m, int[] cap, int LM, int C) {
        for (int i = 0; i < m.nbEBands; i++) {
            int N = (m.eBands[i + 1] - m.eBands[i]) << LM;
            cap[i] = (m.cacheCaps[m.nbEBands * (2 * LM + C - 1) + i] + 64) * C * N >> 2;
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
        celt_decode_test(dec, len, LM, C, end);
        rng = dec.rng();
        return frame_size;
    }

    int celt_decode_test(RangeDecoder dec, int len, int LM, int C, int end) {
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

        int shortBlocks;
        if (isTransient != 0) {
            // shortBlocks = M;
        } else {
            shortBlocks = 0;
        }

        // Decode the global flags (first symbols in the stream)
        int intra_ener = tell + 3 <= total_bits ? dec.ec_dec_bit_logp(3) : 0;
        // Get band energies
        QuantBands.unquant_coarse_energy(mode, end, oldBandE, intra_ener, dec, C, LM);

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
            int width, quanta;
            int dynalloc_loop_logp;
            int boost;
            width = C * (mode.eBands[i + 1] - mode.eBands[i]) << LM;
            /* quanta is 6 bits, but no more than 1 bit/sample
               and no less than 1/8 bit/sample */
            quanta = Math.min(width << BITRES, Math.max(6 << BITRES, width));
            dynalloc_loop_logp = dynalloc_logp;
            boost = 0;
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
            /* Making dynalloc more likely */
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

        QuantBands.unquant_fine_energy(mode, end, oldBandE, fine_quant, dec, C);

        throw new UnsupportedOperationException();
    }
}
