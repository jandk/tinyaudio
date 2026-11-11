package be.twofold.tinyaudio.opus.celt;

import static be.twofold.tinyaudio.opus.celt.Utils.celt_assert;

final class QuantBands {
    private static final int MAX_FINE_BITS = 8;

    private static final float[] pred_coef = {29440 / 32768.0f, 26112 / 32768.0f, 21248 / 32768.0f, 16384 / 32768.0f};
    private static final float[] beta_coef = {30147 / 32768.0f, 22282 / 32768.0f, 12124 / 32768.0f, +6554 / 32768.0f};
    private static final float beta_intra = 4915 / 32768.0f;
    private static final short[][][] e_prob_model = {{
        {72, 127, 65, 129, 66, 128, 65, 128, 64, 128, 62, 128, 64, 128,
            64, 128, 92, 78, 92, 79, 92, 78, 90, 79, 116, 41, 115, 40,
            114, 40, 132, 26, 132, 26, 145, 17, 161, 12, 176, 10, 177, 11},
        {24, 179, 48, 138, 54, 135, 54, 132, 53, 134, 56, 133, 55, 132,
            55, 132, 61, 114, 70, 96, 74, 88, 75, 88, 87, 74, 89, 66,
            91, 67, 100, 59, 108, 50, 120, 40, 122, 37, 97, 43, 78, 50}
    }, {
        {83, 78, 84, 81, 88, 75, 86, 74, 87, 71, 90, 73, 93, 74,
            93, 74, 109, 40, 114, 36, 117, 34, 117, 34, 143, 17, 145, 18,
            146, 19, 162, 12, 165, 10, 178, 7, 189, 6, 190, 8, 177, 9},
        {23, 178, 54, 115, 63, 102, 66, 98, 69, 99, 74, 89, 71, 91,
            73, 91, 78, 89, 86, 80, 92, 66, 93, 64, 102, 59, 103, 60,
            104, 60, 117, 52, 123, 44, 138, 35, 133, 31, 97, 38, 77, 45}
    }, {
        {61, 90, 93, 60, 105, 42, 107, 41, 110, 45, 116, 38, 113, 38,
            112, 38, 124, 26, 132, 27, 136, 19, 140, 20, 155, 14, 159, 16,
            158, 18, 170, 13, 177, 10, 187, 8, 192, 6, 175, 9, 159, 10},
        {21, 178, 59, 110, 71, 86, 75, 85, 84, 83, 91, 66, 88, 73,
            87, 72, 92, 75, 98, 72, 105, 58, 107, 54, 115, 52, 114, 55,
            112, 56, 129, 51, 132, 40, 150, 33, 140, 29, 98, 35, 77, 42}
    }, {
        {42, 121, 96, 66, 108, 43, 111, 40, 117, 44, 123, 32, 120, 36,
            119, 33, 127, 33, 134, 34, 139, 21, 147, 23, 152, 20, 158, 25,
            154, 26, 166, 21, 173, 16, 184, 13, 184, 10, 150, 13, 139, 15},
        {22, 178, 63, 114, 74, 82, 84, 83, 92, 82, 103, 62, 96, 72,
            96, 67, 101, 73, 107, 72, 113, 55, 118, 52, 125, 52, 118, 52,
            117, 55, 135, 49, 137, 39, 157, 32, 145, 29, 97, 33, 77, 40}
    }};
    private static final byte[] small_energy_icdf = {2, 1, 0};

    private QuantBands() {
    }

    static void unquant_coarse_energy(CeltMode m, int end, float[] oldEBands, int intra, RangeDecoder dec, int C, int LM) {
        float coef, beta;
        if (intra != 0) {
            coef = 0;
            beta = beta_intra;
        } else {
            beta = beta_coef[LM];
            coef = pred_coef[LM];
        }

        int budget = dec.storage * 8;

        /* Decode at a fixed coarse resolution */
        float[] prev = {0, 0};
        short[] prob_model = e_prob_model[LM][intra];
        for (int i = 0; i < end; i++) {
            int c = 0;
            do {
                int qi;
                /* It would be better to express this invariant as a
                   test on C at function entry, but that isn't enough
                   to make the static analyzer happy. */
                celt_assert(c < 2);
                int tell = dec.ec_tell();
                if ((budget - tell) >= 15) {
                    int pi = 2 * Math.min(i, 20);
                    qi = dec.ec_laplace_decode(prob_model[pi] << 7, prob_model[pi + 1] << 6);
                } else if ((budget - tell) >= 2) {
                    qi = dec.ec_dec_icdf(small_energy_icdf, 2);
                    qi = (qi >> 1) ^ -(qi & 1);
                } else if ((budget - tell) >= 1) {
                    qi = -dec.ec_dec_bit_logp(1);
                } else {
                    qi = -1;
                }
                float q = (float) qi;

                oldEBands[i + (c * m.nbEBands)] = Math.max(-9.f, oldEBands[i + (c * m.nbEBands)]);
                float tmp = (coef * oldEBands[i + (c * m.nbEBands)]) + prev[c] + q;
                oldEBands[i + (c * m.nbEBands)] = tmp;
                prev[c] = (prev[c] + q) - (beta * q);
            } while (++c < C);
        }
    }

    static void unquant_fine_energy(CeltMode m, int end, float[] oldEBands, int[] fine_quant, RangeDecoder dec, int C) {
        /* Decode finer resolution */
        for (int i = 0; i < end; i++) {
            if (fine_quant[i] <= 0) {
                continue;
            }
            /* Skip the band if its bits don't fit in the packet */
            if (dec.ec_tell() + C * fine_quant[i] > dec.storage * 8) {
                continue;
            }
            int c = 0;
            do {
                int q2;
                float offset;
                q2 = dec.ec_dec_bits(fine_quant[i]);
                offset = (q2 + .5f) * (1 << (14 - fine_quant[i])) * (1.f / 16384) - .5f;
                oldEBands[i + c * m.nbEBands] += offset;
            } while (++c < C);
        }
    }

    static void unquant_energy_finalise(CeltMode m, int end, float[] oldEBands, int[] fine_quant, int[] fine_priority, int bits_left, RangeDecoder dec, int C) {
        /* Use up the remaining bits */
        for (int prio = 0; prio < 2; prio++) {
            for (int i = 0; i < end && bits_left >= C; i++) {
                if (fine_quant[i] >= MAX_FINE_BITS || fine_priority[i] != prio) {
                    continue;
                }
                int c = 0;
                do {
                    int q2;
                    float offset;
                    q2 = dec.ec_dec_bits(1);
                    offset = (q2 - .5f) * (1 << (14 - fine_quant[i] - 1)) * (1.f / 16384);
                    oldEBands[i + c * m.nbEBands] += offset;
                    bits_left--;
                } while (++c < C);
            }
        }
    }
}
