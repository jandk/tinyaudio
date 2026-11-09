package be.twofold.tinyaudio.vorbis;

final class Util {
    private static final int VQ_FEXP = 10;
    private static final int VQ_FMAN = 21;
    private static final int VQ_FEXP_BIAS = 768;

    private Util() {
    }

    static int ilog(int value) {
        return 32 - Integer.numberOfLeadingZeros(value);
    }

    static float float32Unpack(int val) {
        float mant = val & 0x1F_FFFF;
        boolean sign = (val & 0x8000_0000) != 0;
        int exp = (val & 0x7FE0_0000) >>> VQ_FMAN;
        if (sign) {
            mant = -mant;
        }

        exp = exp - (VQ_FMAN - 1) - VQ_FEXP_BIAS;
        exp = Math.max(-63, Math.min(63, exp));
        return Math.scalb(mant, exp);
    }
}
