package be.twofold.tinyaudio.opus.celt;

import static be.twofold.tinyaudio.opus.celt.RangeDecoder.BITRES;
import static be.twofold.tinyaudio.opus.celt.Utils.*;

final class Bands {
    static final int SPREAD_NONE = 0;
    static final int SPREAD_LIGHT = 1;
    static final int SPREAD_NORMAL = 2;
    static final int SPREAD_AGGRESSIVE = 3;

    private static final int QTHETA_OFFSET = 4;
    private static final int QTHETA_OFFSET_TWOPHASE = 16;

    private static final short[] bit_interleave_table = {
            0, 1, 1, 1, 2, 3, 3, 3, 2, 3, 3, 3, 2, 3, 3, 3
    };
    private static final short[] bit_deinterleave_table = {
            0x00, 0x03, 0x0C, 0x0F, 0x30, 0x33, 0x3C, 0x3F,
            0xC0, 0xC3, 0xCC, 0xCF, 0xF0, 0xF3, 0xFC, 0xFF
    };
    private static final short[] exp2_table8 = {
            16384, 17866, 19483, 21247, 23170, 25267, 27554, 30048
    };
    // Indexing table for converting from natural Hadamard to ordery Hadamard
    // This is essentially a bit-reversed Gray, on top of which we've added
    // an inversion of the order because we want the DC at the end rather than
    // the beginning. The lines are for N=2, 4, 8, 16
    private static final byte[] ordery_table = {
            1, 0,
            3, 0, 2, 1,
            7, 0, 4, 3, 6, 1, 5, 2,
            15, 0, 8, 7, 12, 3, 11, 4, 14, 1, 9, 6, 13, 2, 10, 5,
    };

    // State shared by all the calls that code one band
    private final CeltMode m;
    private final int intensity;
    private final int spread;
    private final RangeDecoder ec;
    private final boolean disable_inv;
    private int i;
    private int tf_change;
    private int remaining_bits;
    private int seed;

    // Result of coding the split angle theta
    private boolean sctx_inv;
    private int sctx_imid;
    private int sctx_iside;
    private int sctx_delta;
    private int sctx_itheta;
    private int sctx_qalloc;
    private int sctx_b;
    private int sctx_fill;

    private Bands(
            CeltMode m,
            int intensity,
            int spread,
            RangeDecoder ec,
            boolean disable_inv,
            int seed
    ) {
        this.m = m;
        this.intensity = intensity;
        this.spread = spread;
        this.ec = ec;
        this.disable_inv = disable_inv;
        this.seed = seed;
    }

    /**
     * Decodes the residual of all bands. {@code Y_} is {@code null} for mono, otherwise it is
     * the second channel, starting at {@code Y_Off}. Returns the updated noise seed.
     */
    static int quant_all_bands(
            CeltMode m, int end,
            float[] X_, float[] Y_, int Y_Off, int[] collapse_masks, int[] pulses,
            int shortBlocks, int spread, boolean dual_stereo, int intensity, int[] tf_res,
            int total_bits, int balance, RangeDecoder ec, int LM, int codedBands, int seed,
            boolean disable_inv
    ) {
        Bands ctx = new Bands(m, intensity, spread, ec, disable_inv, seed);
        short[] eBands = m.eBands;
        boolean update_lowband = true;
        int C = Y_ != null ? 2 : 1;

        int M = 1 << LM;
        int B = shortBlocks != 0 ? M : 1;
        float[] norm = new float[C * M * eBands[m.nbEBands]];
        int norm2Off = M * eBands[m.nbEBands];
        float[] lowband_scratch = new float[M * (eBands[m.nbEBands] - eBands[m.nbEBands - 1])];

        int lowband_offset = 0;
        for (int i = 0; i < end; i++) {
            float[] X = X_;
            float[] Y = Y_;
            int XOff = M * eBands[i];
            int YOff = Y_Off + M * eBands[i];
            int N = M * eBands[i + 1] - M * eBands[i];
            int tell = ec.ec_tell_frac();

            // Compute how many bits we want to allocate to this band
            if (i != 0) {
                balance -= tell;
            }
            ctx.i = i;
            int remaining_bits = total_bits - tell - 1;
            ctx.remaining_bits = remaining_bits;
            int b;
            if (i <= codedBands - 1) {
                int curr_balance = balance / Math.min(3, codedBands - i);
                b = Math.max(0, Math.min(16383, Math.min(remaining_bits + 1, pulses[i] + curr_balance)));
            } else {
                b = 0;
            }

            if ((M * eBands[i] - N >= 0 || i == 1) && (update_lowband || lowband_offset == 0)) {
                lowband_offset = i;
            }

            if (i == 1) {
                int n1 = M * eBands[1];
                int n2 = M * (eBands[2] - eBands[1]);
                // Duplicate enough of the first band folding data to be able to fold the second band.
                // Copies no data when the first two bands are equally wide (as in the 48 kHz mode).
                System.arraycopy(norm, 2 * n1 - n2, norm, n1, n2 - n1);
                if (C == 2) {
                    System.arraycopy(norm, norm2Off + 2 * n1 - n2, norm, norm2Off + n1, n2 - n1);
                }
            }

            ctx.tf_change = tf_res[i];
            if (i >= m.effEBands) {
                X = norm;
                XOff = 0;
                if (Y_ != null) {
                    Y = norm;
                    YOff = 0;
                }
            }

            // Get a conservative estimate of the collapse_mask's for the bands we're going to be folding from.
            int effective_lowband = -1;
            int x_cm;
            int y_cm;
            if (lowband_offset != 0 && (spread != SPREAD_AGGRESSIVE || B > 1 || ctx.tf_change < 0)) {
                // This ensures we never repeat spectral content within one band
                effective_lowband = Math.max(0, M * eBands[lowband_offset] - N);
                int fold_start = lowband_offset - 1;
                while (M * eBands[fold_start] > effective_lowband) {
                    fold_start--;
                }
                int fold_end = lowband_offset;
                while (fold_end < i && M * eBands[fold_end] < effective_lowband + N) {
                    fold_end++;
                }
                x_cm = 0;
                y_cm = 0;
                for (int fold_i = fold_start; fold_i < fold_end; fold_i++) {
                    x_cm |= collapse_masks[fold_i * C];
                    y_cm |= collapse_masks[fold_i * C + C - 1];
                }
            } else {
                // Otherwise, we'll be using the LCG to fold, so all blocks will (almost always) be non-zero.
                x_cm = (1 << B) - 1;
                y_cm = (1 << B) - 1;
            }

            if (dual_stereo && i == intensity) {
                // Switch off dual stereo to do intensity
                dual_stereo = false;
                for (int j = 0; j < M * eBands[i]; j++) {
                    norm[j] = .5f * (norm[j] + norm[norm2Off + j]);
                }
            }
            float[] lowband = effective_lowband != -1 ? norm : null;
            int lowbandOff = effective_lowband;
            int lowband2Off = norm2Off + effective_lowband;
            if (dual_stereo) {
                x_cm = ctx.quant_band(X, XOff, N, b / 2, B, lowband, lowbandOff, LM, norm, M * eBands[i], 1.0f, lowband_scratch, 0, x_cm);
                y_cm = ctx.quant_band(Y, YOff, N, b / 2, B, lowband, lowband2Off, LM, norm, norm2Off + M * eBands[i], 1.0f, lowband_scratch, 0, y_cm);
            } else {
                if (Y != null) {
                    x_cm = ctx.quant_band_stereo(X, XOff, Y, YOff, N, b, B, lowband, lowbandOff, LM, norm, M * eBands[i], lowband_scratch, 0, x_cm | y_cm);
                } else {
                    x_cm = ctx.quant_band(X, XOff, N, b, B, lowband, lowbandOff, LM, norm, M * eBands[i], 1.0f, lowband_scratch, 0, x_cm | y_cm);
                }
                y_cm = x_cm;
            }
            collapse_masks[i * C] = x_cm;
            collapse_masks[i * C + C - 1] = y_cm;
            balance += pulses[i] + tell;

            // Update the folding position only as long as we have 1 bit/sample depth
            update_lowband = b > (N << BITRES);
        }
        return ctx.seed;
    }

    /**
     * This prevents energy collapse for transients with multiple short MDCTs.
     */
    static void anti_collapse(
        CeltMode m, float[] X_, int[] collapse_masks, int LM, int C, int size, int end, float[] logE, float[] prev1logE,
        float[] prev2logE, int[] pulses, int seed
    ) {
        for (int i = 0; i < end; i++) {
            int N0 = m.eBands[i + 1] - m.eBands[i];
            // depth in 1/8 bits
            int depth = (1 + pulses[i]) / ((m.eBands[i + 1] - m.eBands[i]) << LM);

            float thresh = .5f * celt_exp2(-.125f * depth);
            float sqrt_1 = celt_rsqrt(N0 << LM);

            for (int c = 0; c < C; c++) {
                float prev1 = prev1logE[c * m.nbEBands + i];
                float prev2 = prev2logE[c * m.nbEBands + i];
                if (C == 1) {
                    prev1 = Math.max(prev1, prev1logE[m.nbEBands + i]);
                    prev2 = Math.max(prev2, prev2logE[m.nbEBands + i]);
                }
                float Ediff = logE[c * m.nbEBands + i] - Math.min(prev1, prev2);
                Ediff = Math.max(0, Ediff);

                // r needs to be multiplied by 2 or 2*sqrt(2) depending on LM because
                // short blocks don't have the same energy as long
                float r = 2.f * celt_exp2(-Ediff);
                if (LM == 3) {
                    r *= 1.41421356f;
                }
                r = Math.min(thresh, r);
                r = r * sqrt_1;
                int XOff = c * size + (m.eBands[i] << LM);
                boolean renormalize = false;
                for (int k = 0; k < 1 << LM; k++) {
                    // Detect collapse
                    if ((collapse_masks[i * C + c] & 1 << k) == 0) {
                        // Fill with noise
                        for (int j = 0; j < N0; j++) {
                            seed = celt_lcg_rand(seed);
                            X_[XOff + (j << LM) + k] = (((seed & 0x8000) != 0) ? r : -r);
                        }
                        renormalize = true;
                    }
                }
                // We just added some energy, so we need to renormalise
                if (renormalize) {
                    Vq.renormalise_vector(X_, XOff, N0 << LM, 1.0f);
                }
            }
        }
    }

    private static int celt_lcg_rand(int seed) {
        return (1664525 * seed) + 1013904223;
    }

    private void deinterleave_hadamard(float[] X, int XOff, int N0, int stride, boolean hadamard) {
        celt_assert(stride > 0);
        int N = N0 * stride;
        float[] tmp = new float[N];
        if (hadamard) {
            int orderyOff = stride - 2;
            for (int i = 0; i < stride; i++) {
                int x = XOff + i;
                int t = ordery_table[orderyOff + i] * N0;
                for (int j = 0; j < N0; j++) {
                    tmp[t + j] = X[x + j * stride];
                }
            }
        } else {
            for (int i = 0; i < stride; i++) {
                int x = XOff + i;
                int t = i * N0;
                for (int j = 0; j < N0; j++) {
                    tmp[t + j] = X[x + j * stride];
                }
            }
        }
        System.arraycopy(tmp, 0, X, XOff, N);
    }

    private void interleave_hadamard(float[] X, int XOff, int N0, int stride, boolean hadamard) {
        int N = N0 * stride;
        float[] tmp = new float[N];
        if (hadamard) {
            int orderyOff = stride - 2;
            for (int i = 0; i < stride; i++) {
                int x = XOff + ordery_table[orderyOff + i] * N0;
                for (int j = 0; j < N0; j++) {
                    tmp[j * stride + i] = X[x + j];
                }
            }
        } else {
            for (int i = 0; i < stride; i++) {
                int x = XOff + i * N0;
                for (int j = 0; j < N0; j++) {
                    tmp[j * stride + i] = X[x + j];
                }
            }
        }
        System.arraycopy(tmp, 0, X, XOff, N);
    }

    private void haar1(float[] X, int XOff, int N0, int stride) {
        N0 >>= 1;
        for (int i = 0; i < stride; i++) {
            int xx = XOff + i;
            for (int j = 0; j < N0; j++) {
                int i1 = xx + stride * 2 * j;
                int i2 = i1 + stride;
                float tmp1 = 0.70710678f * X[i1];
                float tmp2 = 0.70710678f * X[i2];
                X[i1] = tmp1 + tmp2;
                X[i2] = tmp1 - tmp2;
            }
        }
    }

    /**
     * This is a {@code cos()} approximation designed to be bit-exact on any platform. Bit exactness
     * with this approximation is important because it has an impact on the bit allocation.
     */
    private int bitexact_cos(int x) {
        int tmp = (4096 + (x * x)) >> 13;
        celt_assert(tmp <= 32767);

        int x2 = 32767 - tmp + FRAC_MUL16(tmp, -7651 + FRAC_MUL16(tmp, 8277 + FRAC_MUL16(-626, tmp)));
        celt_assert(x2 <= 32766);
        return x2 + 1;
    }

    private int bitexact_log2tan(int isin, int icos) {
        int lc = EC_ILOG(icos);
        int ls = EC_ILOG(isin);
        icos <<= 15 - lc;
        isin <<= 15 - ls;
        return (((ls - lc) * (1 << 11)) + FRAC_MUL16(isin, FRAC_MUL16(isin, -2597) + 7932)) - FRAC_MUL16(icos, FRAC_MUL16(icos, -2597) + 7932);
    }

    /**
     * Uses the second method from http://www.azillionmonkeys.com/qed/sqroot.html.
     * The main idea is to search for the largest binary digit {@code b} such that
     * {@code (g+b)*(g+b) <= _val}, and add it to the solution {@code g}.
     */
    private int isqrt32(int _val) {
        int g = 0;
        for (int bshift = (EC_ILOG(_val) - 1) >> 1; bshift >= 0; bshift--) {
            int b = 1 << bshift;
            int t = ((g << 1) + b) << bshift;
            if (t <= _val) {
                g += b;
                _val -= t;
            }
        }
        return g;
    }

    private int FRAC_MUL16(int a, int b) {
        return (16384 + (a * b)) >> 15;
    }

    private int compute_qn(int N, int b, int offset, int pulse_cap, boolean stereo) {
        int N2 = 2 * N - 1;
        if (stereo && N == 2) {
            N2--;
        }
        // The upper limit ensures that in a stereo split with itheta==16384, we'll
        // always have enough bits left over to code at least one pulse in the
        // side; otherwise it would collapse, since it doesn't get folded.
        int qb = Math.min(b - pulse_cap - (4 << BITRES), (b + N2 * offset) / N2);
        qb = Math.min(8 << BITRES, qb);

        int qn;
        if (qb < (1 << BITRES >> 1)) {
            qn = 1;
        } else {
            qn = exp2_table8[qb & 0x7] >> (14 - (qb >> BITRES));
            qn = (qn + 1) >> 1 << 1;
        }
        celt_assert(qn <= 256);
        return qn;
    }

    /**
     * Decodes the angle theta that splits the band energy between two vectors:
     * the two halves of a mono band, or mid and side of a stereo one.
     */
    private void compute_theta(int N, int b, int B, int B0, int LM, boolean stereo, int fill) {
        // Decide on the resolution to give to the split parameter theta
        int pulse_cap = m.logN[i] + LM * (1 << BITRES);
        int offset = (pulse_cap >> 1) - (stereo && N == 2 ? QTHETA_OFFSET_TWOPHASE : QTHETA_OFFSET);
        int qn = compute_qn(N, b, offset, pulse_cap, stereo);
        if (stereo && i >= this.intensity) {
            qn = 1;
        }
        int tell = ec.ec_tell_frac();

        int itheta = 0;
        boolean inv = false;
        if (qn != 1) {
            // Entropy coding of the angle. We use a uniform pdf for the
            // time split, a step for stereo, and a triangular one for the rest.
            if (stereo && N > 2) {
                int p0 = 3;
                int x0 = qn / 2;
                int ft = p0 * (x0 + 1) + x0;
                // Use a probability of p0 up to itheta=8192 and then use 1 after
                int fs = ec.ec_decode(ft);
                int x = fs < (x0 + 1) * p0
                        ? fs / p0
                        : x0 + 1 + (fs - (x0 + 1) * p0);
                ec.ec_dec_update(
                        x <= x0 ? p0 * x : (x - 1 - x0) + (x0 + 1) * p0,
                        x <= x0 ? p0 * (x + 1) : (x - x0) + (x0 + 1) * p0,
                        ft);
                itheta = x;
            } else if (B0 > 1 || stereo) {
                // Uniform pdf
                itheta = ec.ec_dec_uint(qn + 1);
            } else {
                // Triangular pdf
                int ft = ((qn >> 1) + 1) * ((qn >> 1) + 1);
                int fm = ec.ec_decode(ft);

                int fs;
                int fl;
                if (fm < ((qn >> 1) * ((qn >> 1) + 1) >> 1)) {
                    itheta = (isqrt32(8 * fm + 1) - 1) >> 1;
                    fs = itheta + 1;
                    fl = itheta * (itheta + 1) >> 1;
                } else {
                    itheta = (2 * (qn + 1) - isqrt32(8 * (ft - fm - 1) + 1)) >> 1;
                    fs = qn + 1 - itheta;
                    fl = ft - ((qn + 1 - itheta) * (qn + 2 - itheta) >> 1);
                }

                ec.ec_dec_update(fl, fl + fs, ft);
            }
            itheta = itheta * 16384 / qn;
        } else if (stereo) {
            if (b > 2 << BITRES && this.remaining_bits > 2 << BITRES) {
                inv = ec.ec_dec_bit_logp(2) != 0;
            }
            // inv flag override to avoid problems with downmixing (RFC 8251, Section 10)
            if (this.disable_inv) {
                inv = false;
            }
        }
        int qalloc = ec.ec_tell_frac() - tell;
        b -= qalloc;

        if (itheta == 0) {
            sctx_imid = 32767;
            sctx_iside = 0;
            sctx_delta = -16384;
            fill &= (1 << B) - 1;
        } else if (itheta == 16384) {
            sctx_imid = 0;
            sctx_iside = 32767;
            sctx_delta = 16384;
            fill &= ((1 << B) - 1) << B;
        } else {
            sctx_imid = bitexact_cos(itheta);
            sctx_iside = bitexact_cos(16384 - itheta);
            // This is the mid vs side allocation that minimizes squared error in that band.
            sctx_delta = FRAC_MUL16((N - 1) << 7, bitexact_log2tan(sctx_iside, sctx_imid));
        }

        sctx_inv = inv;
        sctx_itheta = itheta;
        sctx_qalloc = qalloc;
        sctx_b = b;
        sctx_fill = fill;
    }

    /**
     * Special case for one sample: just a sign per channel.
     */
    private int quant_band_n1(float[] X, int XOff, float[] Y, int YOff, float[] lowband_out, int lowband_outOff) {
        X[XOff] = readFloatSign();
        if (Y != null) {
            Y[YOff] = readFloatSign();
        }
        if (lowband_out != null) {
            lowband_out[lowband_outOff] = X[XOff];
        }
        return 1;
    }

    private float readFloatSign() {
        if (remaining_bits < 1 << BITRES) {
            return 1.0f;
        }
        remaining_bits -= 1 << BITRES;
        return ec.ec_dec_bits(1) != 0 ? -1.0f : 1.0f;
    }

    /**
     * Decodes a mono partition. It can split the band in two and transmit the energy
     * difference between the two half-bands. It is called recursively, so a band can
     * end up split in 8 parts.
     */
    private int quant_partition(
            float[] X, int XOff, int N, int b, int B,
            float[] lowband, int lowbandOff, int LM, float gain, int fill
    ) {
        int B0 = B;
        int cm = 0;

        // If we need 1.5 more bit than we can produce, split the band in two.
        short[] cache = m.cacheBits;
        int cacheOff = m.cacheIndex[(LM + 1) * m.nbEBands + i];
        if (LM != -1 && b > cache[cacheOff + cache[cacheOff]] + 12 && N > 2) {
            N >>= 1;
            LM -= 1;
            if (B == 1) {
                fill = (fill & 1) | (fill << 1);
            }
            B = (B + 1) >> 1;

            compute_theta(N, b, B, B0, LM, false, fill);
            b = sctx_b;
            fill = sctx_fill;
            float mid = sctx_imid * (1.0f / 32768.0f);
            float side = sctx_iside * (1.0f / 32768.0f);
            int delta = sctx_delta;
            int itheta = sctx_itheta;

            // Give more bits to low-energy MDCTs than they would otherwise deserve
            if (B0 > 1 && (itheta & 0x3fff) != 0) {
                if (itheta > 8192) {
                    // Rough approximation for pre-echo masking
                    delta -= delta >> (4 - LM);
                } else {
                    // Corresponds to a forward-masking slope of 1.5 dB per 10 ms
                    delta = Math.min(0, delta + (N << BITRES >> (5 - LM)));
                }
            }
            int mbits = Math.max(0, Math.min(b, (b - delta) / 2));
            int sbits = b - mbits;
            remaining_bits -= sctx_qalloc;

            int rebalance = remaining_bits;
            if (mbits >= sbits) {
                cm = quant_partition(X, XOff, N, mbits, B, lowband, lowbandOff, LM, gain * mid, fill);
                rebalance = mbits - (rebalance - remaining_bits);
                if (rebalance > 3 << BITRES && itheta != 0) {
                    sbits += rebalance - (3 << BITRES);
                }
                cm |= quant_partition(X, XOff + N, N, sbits, B, lowband, lowbandOff + N, LM, gain * side, fill >> B) << (B0 >> 1);
            } else {
                cm = quant_partition(X, XOff + N, N, sbits, B, lowband, lowbandOff + N, LM, gain * side, fill >> B) << (B0 >> 1);
                rebalance = sbits - (rebalance - remaining_bits);
                if (rebalance > 3 << BITRES && itheta != 16384) {
                    mbits += rebalance - (3 << BITRES);
                }
                cm |= quant_partition(X, XOff, N, mbits, B, lowband, lowbandOff, LM, gain * mid, fill);
            }
        } else {
            // This is the basic no-split case
            int q = Rate.bits2pulses(m, i, LM, b);
            int curr_bits = Rate.pulses2bits(m, i, LM, q);
            remaining_bits -= curr_bits;

            // Ensures we can never bust the budget
            while (remaining_bits < 0 && q > 0) {
                remaining_bits += curr_bits;
                q--;
                curr_bits = Rate.pulses2bits(m, i, LM, q);
                remaining_bits -= curr_bits;
            }

            if (q != 0) {
                int K = Rate.get_pulses(q);

                // Finally decode the pulses
                cm = Vq.alg_unquant(X, XOff, N, K, spread, B, ec, gain);
            } else {
                // If there's no pulse, fill the band anyway
                int cm_mask = (1 << B) - 1;
                fill &= cm_mask;
                if (fill == 0) {
                    for (int j = 0; j < N; j++) {
                        X[XOff + j] = 0;
                    }
                } else {
                    if (lowband == null) {
                        // Noise
                        for (int j = 0; j < N; j++) {
                            seed = celt_lcg_rand(seed);
                            X[XOff + j] = (float) (seed >> 20);
                        }
                        cm = cm_mask;
                    } else {
                        // Folded spectrum
                        for (int j = 0; j < N; j++) {
                            seed = celt_lcg_rand(seed);
                            // About 48 dB below the "normal" folding level
                            float tmp = (seed & 0x8000) != 0 ? 1.0f / 256 : -1.0f / 256;
                            X[XOff + j] = lowband[lowbandOff + j] + tmp;
                        }
                        cm = fill;
                    }
                    Vq.renormalise_vector(X, XOff, N, gain);
                }
            }
        }

        return cm;
    }

    /**
     * Decodes a mono band: it applies the time-frequency change ({@code tf_change})
     * and then decodes the band as one partition.
     */
    private int quant_band(
            float[] X, int XOff, int N, int b, int B,
            float[] lowband, int lowbandOff, int LM, float[] lowband_out, int lowband_outOff,
            float gain, float[] lowband_scratch, int lowband_scratchOff, int fill
    ) {
        boolean longBlocks = B == 1;
        int N_B = N / B;

        // Special case for one sample
        if (N == 1) {
            return quant_band_n1(X, XOff, null, 0, lowband_out, lowband_outOff);
        }

        int tf_change = this.tf_change;
        int recombine = Math.max(tf_change, 0);

        // Band recombining to increase frequency resolution
        if (lowband != null && (recombine != 0 || ((N_B & 1) == 0 && tf_change < 0) || B > 1)) {
            System.arraycopy(lowband, lowbandOff, lowband_scratch, lowband_scratchOff, N);
            lowband = lowband_scratch;
            lowbandOff = lowband_scratchOff;
        }

        for (int k = 0; k < recombine; k++) {
            if (lowband != null) {
                haar1(lowband, lowbandOff, N >> k, 1 << k);
            }
            fill = bit_interleave_table[fill & 0xF] | bit_interleave_table[fill >> 4] << 2;
        }

        B >>= recombine;
        N_B <<= recombine;
        int time_divide = 0;

        // Increasing the time resolution
        while ((N_B & 1) == 0 && tf_change < 0) {
            if (lowband != null) {
                haar1(lowband, lowbandOff, N_B, B);
            }
            fill |= fill << B;
            B <<= 1;
            N_B >>= 1;
            time_divide++;
            tf_change++;
        }

        // Reorganize the samples in time order instead of frequency order
        if (B > 1 && lowband != null) {
            deinterleave_hadamard(lowband, lowbandOff, N_B >> recombine, B << recombine, longBlocks);
        }

        int cm = quant_partition(X, XOff, N, b, B, lowband, lowbandOff, LM, gain, fill);

        // Undo the sample reorganization going from time order to frequency order
        if (B > 1) {
            interleave_hadamard(X, XOff, N_B >> recombine, B << recombine, longBlocks);
        }

        // Undo time-freq changes that we did earlier
        for (int k = 0; k < time_divide; k++) {
            B >>= 1;
            N_B <<= 1;
            cm |= cm >> B;
            haar1(X, XOff, N_B, B);
        }

        for (int k = 0; k < recombine; k++) {
            cm = bit_deinterleave_table[cm];
            haar1(X, XOff, N >> k, 1 << k);
        }
        B <<= recombine;

        // Scale output for later folding
        if (lowband_out != null) {
            float n = celt_sqrt(N);
            for (int j = 0; j < N; j++) {
                lowband_out[lowband_outOff + j] = n * X[XOff + j];
            }
        }
        cm &= (1 << B) - 1;
        return cm;
    }

    /**
     * Decodes a band for the stereo case: it decodes the mid/side angle,
     * then mid and side as mono bands.
     */
    private int quant_band_stereo(
            float[] X, int XOff, float[] Y, int YOff, int N, int b, int B,
            float[] lowband, int lowbandOff, int LM, float[] lowband_out, int lowband_outOff,
            float[] lowband_scratch, int lowband_scratchOff, int fill
    ) {
        // Special case for one sample
        if (N == 1) {
            return quant_band_n1(X, XOff, Y, YOff, lowband_out, lowband_outOff);
        }

        int orig_fill = fill;

        compute_theta(N, b, B, B, LM, true, fill);
        boolean inv = sctx_inv;
        b = sctx_b;
        fill = sctx_fill;
        float mid = (1.0f / 32768.0f) * sctx_imid;
        float side = (1.0f / 32768.0f) * sctx_iside;
        int itheta = sctx_itheta;

        int cm;
        if (N == 2) {
            // This is a special case for N=2 that only works for stereo and takes
            // advantage of the fact that mid and side are orthogonal to encode
            // the side with just one bit.
            int sbits = 0;
            // Only need one bit for the side
            if (itheta != 0 && itheta != 16384) {
                sbits = 1 << BITRES;
            }
            int mbits = b - sbits;
            remaining_bits -= sctx_qalloc + sbits;

            boolean c = itheta > 8192;
            float[] x2 = c ? Y : X;
            int x2Off = c ? YOff : XOff;
            float[] y2 = c ? X : Y;
            int y2Off = c ? XOff : YOff;
            // The side is just a sign
            int sign = sbits != 0 ? 1 - 2 * ec.ec_dec_bits(1) : 1;

            // We use orig_fill here because we want to fold the side, but if
            // itheta==16384, we'll have cleared the low bits of fill.
            cm = quant_band(x2, x2Off, N, mbits, B, lowband, lowbandOff, LM, lowband_out, lowband_outOff, 1.0f, lowband_scratch, lowband_scratchOff, orig_fill);
            // We don't split N=2 bands, so cm is either 1 or 0 (for a fold-collapse),
            // and there's no need to worry about mixing with the other channel.
            y2[y2Off] = -sign * x2[x2Off + 1];
            y2[y2Off + 1] = sign * x2[x2Off];

            float x0 = mid * X[XOff];
            float x1 = mid * X[XOff + 1];
            float y0 = side * Y[YOff];
            float y1 = side * Y[YOff + 1];
            X[XOff] = x0 - y0;
            Y[YOff] = x0 + y0;
            X[XOff + 1] = x1 - y1;
            Y[YOff + 1] = x1 + y1;
        } else {
            // "Normal" split code
            int mbits = Math.max(0, Math.min(b, (b - sctx_delta) / 2));
            int sbits = b - mbits;
            remaining_bits -= sctx_qalloc;

            int rebalance = remaining_bits;
            if (mbits >= sbits) {
                // In stereo mode, we do not apply a scaling to the mid because we need the normalized
                // mid for folding later
                cm = quant_band(X, XOff, N, mbits, B, lowband, lowbandOff, LM, lowband_out, lowband_outOff, 1.0f, lowband_scratch, lowband_scratchOff, fill);
                rebalance = mbits - (rebalance - remaining_bits);
                if (rebalance > 3 << BITRES && itheta != 0) {
                    sbits += rebalance - (3 << BITRES);
                }

                // For a stereo split, the high bits of fill are always zero, so no
                // folding will be done to the side.
                cm |= quant_band(Y, YOff, N, sbits, B, null, 0, LM, null, 0, side, null, 0, fill >> B);
            } else {
                // For a stereo split, the high bits of fill are always zero, so no
                // folding will be done to the side.
                cm = quant_band(Y, YOff, N, sbits, B, null, 0, LM, null, 0, side, null, 0, fill >> B);
                rebalance = sbits - (rebalance - remaining_bits);
                if (rebalance > 3 << BITRES && itheta != 16384) {
                    mbits += rebalance - (3 << BITRES);
                }
                // In stereo mode, we do not apply a scaling to the mid because we need the normalized
                // mid for folding later
                cm |= quant_band(X, XOff, N, mbits, B, lowband, lowbandOff, LM, lowband_out, lowband_outOff, 1.0f, lowband_scratch, lowband_scratchOff, fill);
            }
        }

        if (N != 2) {
            stereo_merge(X, XOff, Y, YOff, mid, N);
        }
        if (inv) {
            for (int j = 0; j < N; j++) {
                Y[YOff + j] = -Y[YOff + j];
            }
        }
        return cm;
    }

    private void stereo_merge(float[] X, int XOff, float[] Y, int YOff, float mid, int N) {
        // Compute the norm of X+Y and X-Y as |X|^2 + |Y|^2 +/- sum(xy)
        float xp = 0;
        float side = 0;
        for (int j = 0; j < N; j++) {
            float yj = Y[YOff + j];
            xp += X[XOff + j] * yj;
            side += yj * yj;
        }

        // Compensating for the mid normalization
        xp = mid * xp;
        float El = mid * mid + side - 2 * xp;
        float Er = mid * mid + side + 2 * xp;
        if (Er < 6e-4f || El < 6e-4f) {
            System.arraycopy(X, XOff, Y, YOff, N);
            return;
        }

        float lgain = celt_rsqrt(El);
        float rgain = celt_rsqrt(Er);

        for (int j = 0; j < N; j++) {
            // Apply mid scaling (side is already scaled)
            float l = mid * X[XOff + j];
            float r = Y[YOff + j];
            X[XOff + j] = lgain * (l - r);
            Y[YOff + j] = rgain * (l + r);
        }
    }
}
