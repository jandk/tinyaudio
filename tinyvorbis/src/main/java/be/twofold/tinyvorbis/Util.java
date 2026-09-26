package be.twofold.tinyvorbis;

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

    static long float32Pack(float val) {
        int sign = 0;
        if (val < 0) {
            sign = 0x80000000;
            val = -val;
        }

        int exp = (int) Math.floor(Math.log(val) / Math.log(2.0) + 0.001);
        int mant = (int) Math.rint(Math.scalb(val, (VQ_FMAN - 1) - exp));
        exp = (exp + VQ_FEXP_BIAS) << VQ_FMAN;

        return sign | exp | mant;
    }

    static String describe(byte[] data) {
        StringBuilder sb = new StringBuilder(data.length + " bytes");
        if (data.length > 0) {
            sb.append(" starting with");
            for (int i = 0; i < Math.min(data.length, 8); i++) {
                sb.append(String.format(" %02X", data[i]));
            }
        }
        return sb.toString();
    }
}
