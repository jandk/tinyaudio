package be.twofold.tinyaudio.opus.celt;

final class Utils {
    private Utils() {
    }

    static void celt_assert(boolean condition) {
        assert condition;
    }

    static int EC_ILOG(int x) {
        return Integer.SIZE - Integer.numberOfLeadingZeros(x);
    }
}
