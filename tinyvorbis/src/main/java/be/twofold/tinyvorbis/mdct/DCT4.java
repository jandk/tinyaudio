package be.twofold.tinyvorbis.mdct;

public final class DCT4 {
    private final int n;
    private final FFT fft;
    private final float[] twiddle;
    private final float[] scratch;

    public DCT4(int n) {
        if (n < 4 || n > 65536 || n % 4 != 0) {
            throw new IllegalArgumentException("n must be a multiple of 4 between 4 and 65536");
        }
        this.n = n;
        this.fft = new FFT(n / 2);
        this.twiddle = buildTwiddle(n);
        this.scratch = new float[n];
    }

    /**
     * rot(-(i + 1/8) / (2n)) for i < n/2: the half-sample shifts of the
     * DCT-IV kernel as rotations, with the constant quarter split evenly
     * over the pre- and post-twiddle.
     */
    static float[] buildTwiddle(int n) {
        float[] twiddle = new float[n];
        for (int i = 0, lim = n / 2; i < lim; i++) {
            double angle = 2 * Math.PI * (i + 0.125) / (2 * n);
            twiddle[i * 2/**/] = (float) Math.cos(angle);
            twiddle[i * 2 + 1] = (float) -Math.sin(angle);
        }
        return twiddle;
    }

    public void transform(float[] x) {
        transform(x, x);
    }

    public void transform(float[] in, float[] out) {
        if (in.length != n || out.length != n) {
            throw new IllegalArgumentException("array length must be equal to n");
        }
        preTwiddle(twiddle, in, scratch, n);
        fft.forward(scratch, out);
        postTwiddle(twiddle, out);
    }

    /**
     * Pair + pre-twiddle: z[k] = (u[2k] + i*u[n-1-2k]) * w[k], in → out.
     */
    static void preTwiddle(float[] w, float[] in, float[] out, int n) {
        for (int a = 0, b = n - 1; a < n; a += 2, b -= 2) {
            float xr = in[a], xi = in[b];
            float wr = w[a], wi = w[a + 1];
            out[a/**/] = Math.fma(xr, wr, -xi * wi);
            out[a + 1] = Math.fma(xr, wi, +xi * wr);
        }
    }

    /**
     * Post-twiddle + unpair, in place: W = Z[k] * w[k], C[2k] = Re W,
     * C[n-1-2k] = -Im W. The -Im of slot k lands in the imaginary half of
     * slot n/2-1-k and vice versa, so the two slots are processed together.
     */
    static void postTwiddle(float[] w, float[] x) {
        int n = x.length;
        for (int a = 0, b = n - 2, lim = n / 2; a < lim; a += 2, b -= 2) {
            float x0r = x[a], x0i = x[a + 1];
            float x1r = x[b], x1i = x[b + 1];
            float w0r = w[a], w0i = w[a + 1];
            float w1r = w[b], w1i = w[b + 1];
            x[a/**/] = Math.fma(+x0r, w0r, -x0i * w0i);
            x[a + 1] = Math.fma(-x1r, w1i, -x1i * w1r);
            x[b/**/] = Math.fma(+x1r, w1r, -x1i * w1i);
            x[b + 1] = Math.fma(-x0r, w0i, -x0i * w0r);
        }
    }
}
