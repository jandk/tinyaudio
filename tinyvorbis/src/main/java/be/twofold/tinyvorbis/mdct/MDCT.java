package be.twofold.tinyvorbis.mdct;

/**
 * Inverse modified discrete cosine transform of size n, in the Vorbis
 * convention and unscaled as in libvorbis:
 * <pre>
 *   y[k] = sum_m X[m] * cos(pi/(n/2) * (k + 1/2 + n/4) * (m + 1/2))
 * </pre>
 * Computed as DCT-IV → unfold. The DCT-IV is the same pre-twiddle →
 * n/4-point FFT → post-twiddle as {@link DCT4}, sharing its table and
 * pre-twiddle; the post-twiddle here writes straight into the unfolded
 * positions instead of back into a coefficient array.
 */
public final class MDCT {
    private final int n;
    private final FFT fft;
    private final float[] twiddle;
    private final float[] bufA;
    private final float[] bufB;

    public MDCT(int n) {
        if (n < 8 || n > 131072 || n % 8 != 0) {
            throw new IllegalArgumentException("n must be a multiple of 8 between 8 and 131072");
        }
        this.n = n;
        this.fft = new FFT(n / 4);
        this.twiddle = DCT4.buildTwiddle(n / 2);
        this.bufA = new float[n / 2];
        this.bufB = new float[n / 2];
    }

    /**
     * n/2 coefficients in, n samples out, unscaled.
     */
    public void backward(float[] in, float[] out) {
        if (in.length < n / 2 || out.length < n) {
            throw new IllegalArgumentException("in must have n/2 coefficients and out room for n samples");
        }
        DCT4.preTwiddle(twiddle, in, bufA, n / 2);
        fft.forward(bufA, bufB);
        postTwiddleUnfold(bufB, out);
    }

    /**
     * DCT-IV post-twiddle and unfold in one pass. The post-twiddle would put
     * Re W at v[2k] and -Im W at v[2q-1-2k]; the unfold [a' b'] → [b', -b'_r,
     * -a'_r, -a'] sends every v[j] to y[3q-1-j] negated, and to y[j-q] as is
     * (j >= q) or y[3q+j] negated (j < q). Substituting gives two regimes,
     * split at k = q/2, each writing four samples per twiddled point.
     */
    private void postTwiddleUnfold(float[] x, float[] y) {
        float[] w = twiddle;
        int q = n / 4;

        int s0 = 0;
        int s1 = 2 * q - 2;
        int p1 = /**/q - 2;
        int p2 = /**/q;
        int p3 = 3 * q - 2;
        int p4 = 3 * q;
        while (s0 < q) {
            float x0r = x[s0], x0i = x[s0 + 1];
            float w0r = w[s0], w0i = w[s0 + 1];
            float re0 = Math.fma(x0r, w0r, -x0i * w0i);
            float im0 = Math.fma(x0r, w0i, +x0i * w0r);

            float x1r = x[s1], x1i = x[s1 + 1];
            float w1r = w[s1], w1i = w[s1 + 1];
            float re1 = Math.fma(x1r, w1r, -x1i * w1i);
            float im1 = Math.fma(x1r, w1i, +x1i * w1r);

            // @formatter:off
            y[p1] = +re1; y[p1 + 1] = -im0; // quarter 1
            y[p2] = +im0; y[p2 + 1] = -re1; // quarter 2
            y[p3] = +im1; y[p3 + 1] = -re0; // quarter 3
            y[p4] = -re0; y[p4 + 1] = +im1; // quarter 4
            // @formatter:on

            s0 += 2;
            s1 -= 2;
            p1 -= 2;
            p2 += 2;
            p3 -= 2;
            p4 += 2;
        }
    }
}
