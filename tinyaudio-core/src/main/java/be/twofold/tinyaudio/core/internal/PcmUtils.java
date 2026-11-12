package be.twofold.tinyaudio.core.internal;

import java.util.Objects;

public final class PcmUtils {
    private PcmUtils() {
    }

    public static short floatToPcm(float f) {
        return (short) Math.rint(MathUtils.clamp(f * 32768.0f, -32768.0f, +32767.0f));
    }

    public static void floatToPcm(float[] src, int srcOff, int srcStride, short[] dst, int dstOff, int count) {
        int srcSize = count == 0 ? 0 : (count - 1) * srcStride + 1;
        Objects.checkFromIndexSize(srcOff, srcSize, src.length);
        Objects.checkFromIndexSize(dstOff, count, dst.length);
        if (srcStride <= 0) {
            throw new IllegalArgumentException("srcStride must be positive");
        }

        for (int i = srcOff, o = dstOff; i < srcOff + srcSize; i += srcStride, o++) {
            dst[o] = floatToPcm(src[i]);
        }
    }
}
