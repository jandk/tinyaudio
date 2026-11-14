package be.twofold.tinyaudio.flac.internal;

import be.twofold.tinyaudio.core.internal.BitReader;
import be.twofold.tinyaudio.flac.FlacException;

import java.util.Objects;

final class FlacSubFrameHeader {
    private final Type subframeType;
    private final int order;
    private final int wastedBits;

    FlacSubFrameHeader(Type subframeType, int order, int wastedBits) {
        this.subframeType = Objects.requireNonNull(subframeType);
        this.order = order;
        this.wastedBits = wastedBits;
    }

    static FlacSubFrameHeader read(BitReader bits) throws FlacException {
        if (bits.readBool()) {
            throw new FlacException("Invalid subframe header");
        }

        int subframeTypeBits = bits.read(6);
        Type subframeType = subframeType(subframeTypeBits);
        int order = order(subframeTypeBits);
        int wastedBits = bits.readBool() ? bits.readUnary() + 1 : 0;

        return new FlacSubFrameHeader(
            subframeType,
            order,
            wastedBits
        );
    }

    private static Type subframeType(int bits) throws FlacException {
        if (bits == 0) {
            return Type.CONSTANT;
        } else if (bits == 1) {
            return Type.VERBATIM;
        } else if (bits >= 8 && bits <= 12) {
            return Type.FIXED;
        } else if (bits >= 32 && bits <= 63) {
            return Type.LPC;
        } else {
            throw new FlacException("Invalid subframe type");
        }
    }

    private static int order(int bits) {
        if (bits >= 8 && bits <= 12) {
            return bits - 8;
        } else if (bits >= 32 && bits <= 63) {
            return bits - 31;
        } else {
            return 0;
        }
    }

    Type getSubframeType() {
        return subframeType;
    }

    int getOrder() {
        return order;
    }

    int getWastedBits() {
        return wastedBits;
    }

    enum Type {
        CONSTANT,
        VERBATIM,
        FIXED,
        LPC,
    }
}
