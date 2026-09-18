package be.twofold.tinyvorbis;

final class Util {
    private Util() {
    }

    static int ilog(int value) {
        return 32 - Integer.numberOfLeadingZeros(value);
    }

    static float float32Unpack(int val) {
        int mantissa = val & 0x1fffff;
        int sign = val & 0x80000000;
        int exponent = (val & 0x7fe00000) >>> 21;
        if (sign != 0) {
            mantissa = -mantissa;
        }
        return Math.scalb(mantissa, exponent - 20 - 768);
    }
}
