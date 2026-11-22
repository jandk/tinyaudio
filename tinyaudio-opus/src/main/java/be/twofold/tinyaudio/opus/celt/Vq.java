package be.twofold.tinyaudio.opus.celt;

import static be.twofold.tinyaudio.opus.celt.Bands.SPREAD_NONE;
import static be.twofold.tinyaudio.opus.celt.Utils.*;

final class Vq {
    private static final byte[] SPREAD_FACTOR = {15, 10, 5};

    private Vq() {
    }

    /**
     * Decode pulse vector and combine the result with the pitch vector to produce
     * the final normalised signal in the current band.
     */
    static int alg_unquant(
        float[] X, int XOff, int N, int K, int spread, int B,
        RangeDecoder dec, float gain
    ) {
        celt_assert2(K > 0, "alg_unquant() needs at least one pulse");
        celt_assert2(N > 1, "alg_unquant() needs at least two dimensions");
        int[] iy = new int[N];
        float Ryy = Cwrs.decode_pulses(iy, N, K, dec);
        normalise_residual(iy, X, XOff, N, Ryy, gain);
        exp_rotation(X, XOff, N, B, K, spread);
        return extract_collapse_mask(iy, N, B);
    }

    /**
     * Takes the pitch vector and the decoded residual vector, computes the gain
     * that will give ||p+g*y||=1 and mixes the residual with the pitch.
     */
    private static void normalise_residual(
        int[] iy, float[] X, int XOff,
        int N, float Ryy, float gain
    ) {
        float g = celt_rsqrt(Ryy) * gain;
        for (int i = 0; i < N; i++) {
            X[XOff + i] = g * (float) iy[i];
        }
    }

    private static void exp_rotation(float[] X, int XOff, int len, int stride, int K, int spread) {
        if (2 * K >= len || spread == SPREAD_NONE) {
            return;
        }
        int factor = SPREAD_FACTOR[spread - 1];

        float gain = (float) len / (len + factor * K);
        float theta = .5f * (gain * gain);

        float c = celt_cos_norm(theta);
        float s = celt_cos_norm(1.0f - theta); // sin(theta)

        int stride2 = 0;
        if (len >= 8 * stride) {
            stride2 = 1;
            // This is just a simple (equivalent) way of computing sqrt(len/stride) with rounding.
            // It's basically incrementing long as (stride2+0.5)^2 < len/stride.
            while ((stride2 * stride2 + stride2) * stride + (stride >> 2) < len) {
                stride2++;
            }
        }
        // NOTE: As a minor optimization, we could be passing around log2(B), not B, for both this and for
        // extract_collapse_mask().
        len /= stride;
        for (int i = 0; i < stride; i++) {
            if (stride2 != 0) {
                exp_rotation1(X, XOff + i * len, len, stride2, s, c);
            }
            exp_rotation1(X, XOff + i * len, len, 1, c, s);
        }
    }

    private static int extract_collapse_mask(int[] iy, int N, int B) {
        if (B <= 1) {
            return 1;
        }
        // NOTE: As a minor optimization, we could be passing around log2(B), not B, for both this and for
        // exp_rotation().
        int N0 = N / B;
        int collapse_mask = 0;
        for (int i = 0; i < B; i++) {
            for (int j = 0; j < N0; j++) {
                collapse_mask |= ((iy[i * N0 + j] != 0) ? 1 : 0) << i;
            }
        }
        return collapse_mask;
    }

    private static void exp_rotation1(float[] X, int XOff, int len, int stride, float c, float s) {
        int x = XOff;
        for (int i = 0; i < len - stride; i++) {
            float x1 = X[x];
            float x2 = X[x + stride];
            X[x + stride] = c * x2 + s * x1;
            X[x++] = c * x1 - s * x2;
        }

        x = XOff + len - 2 * stride - 1;
        for (int i = len - 2 * stride - 1; i >= 0; i--) {
            float x1 = X[x];
            float x2 = X[x + stride];
            X[x + stride] = c * x2 + s * x1;
            X[x--] = c * x1 - s * x2;
        }
    }

    static void renormalise_vector(float[] X, int XOff, int N, float gain) {
        float E = EPSILON;
        for (int i = XOff; i < XOff + N; i++) {
            float xi = X[i];
            E += xi * xi;
        }

        float g = celt_rsqrt(E) * gain;
        for (int i = XOff; i < XOff + N; i++) {
            X[i] *= g;
        }
    }
}
