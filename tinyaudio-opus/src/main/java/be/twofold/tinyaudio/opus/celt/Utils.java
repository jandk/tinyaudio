package be.twofold.tinyaudio.opus.celt;

final class Utils {
    static final float EPSILON = 1e-15f;

    private Utils() {
    }

    static int EC_ILOG(int x) {
        return Integer.SIZE - Integer.numberOfLeadingZeros(x);
    }

    static void celt_assert(boolean condition) {
        assert condition;
    }

    static void celt_assert2(boolean condition, String message) {
        assert condition : message;
    }

    static float celt_cos_norm(float x) {
        return (float) Math.cos(.5f * Math.PI * x);
    }

    static float celt_exp2(float x) {
        return (float) Math.exp(0.6931471805599453094 * x);
    }

    static float celt_rsqrt(float x) {
        return 1.f / celt_sqrt(x);
    }

    static float celt_sqrt(float x) {
        return (float) Math.sqrt(x);
    }
}
