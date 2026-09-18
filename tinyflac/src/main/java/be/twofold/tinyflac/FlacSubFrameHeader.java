package be.twofold.tinyflac;

import java.io.IOException;
import java.util.Objects;

public record FlacSubFrameHeader(
    Type subframeType,
    int order,
    int wastedBits
) {
    public FlacSubFrameHeader {
        Objects.requireNonNull(subframeType);
    }

    public static FlacSubFrameHeader read(BitInputStream is) throws IOException {
        if (is.readBool()) {
            throw new IOException("Invalid subframe header");
        }

        int subframeTypeBits = is.read(6);
        Type subframeType = subframeType(subframeTypeBits);
        int order = order(subframeTypeBits);
        int wastedBits = is.readBool() ? is.readUnary() + 1 : 0;

        return new FlacSubFrameHeader(
            subframeType,
            order,
            wastedBits
        );
    }

    private static Type subframeType(int bits) throws IOException {
        if (bits == 0) {
            return Type.CONSTANT;
        } else if (bits == 1) {
            return Type.VERBATIM;
        } else if (bits >= 8 && bits <= 12) {
            return Type.FIXED;
        } else if (bits >= 32 && bits <= 63) {
            return Type.LPC;
        } else {
            throw new IOException("Invalid subframe type");
        }
    }

    private static int order(int bits) throws IOException {
        if (bits >= 8 && bits <= 12) {
            return bits - 8;
        } else if (bits >= 32 && bits <= 63) {
            return bits - 31;
        } else {
            return 0;
        }
    }

    public enum Type {
        CONSTANT,
        VERBATIM,
        FIXED,
        LPC,
    }
}
