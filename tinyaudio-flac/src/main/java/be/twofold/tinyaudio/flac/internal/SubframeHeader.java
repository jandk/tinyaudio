package be.twofold.tinyaudio.flac.internal;

import be.twofold.tinyaudio.core.AudioFormatException;
import be.twofold.tinyaudio.core.internal.BitReader;

import java.util.Objects;

final class SubframeHeader {
    private final SubframeType type;
    private final int order;
    private final int wastedBits;

    SubframeHeader(SubframeType type, int order, int wastedBits) {
        this.type = Objects.requireNonNull(type);
        this.order = order;
        this.wastedBits = wastedBits;
    }

    static SubframeHeader read(BitReader bits) throws AudioFormatException {
        if (bits.readBool()) {
            throw new AudioFormatException("Invalid subframe header");
        }

        int typeBits = bits.read(6);
        SubframeType type = parseType(typeBits);
        int order = parseOrder(typeBits);
        int wastedBits = bits.readBool() ? bits.readUnary() + 1 : 0;

        return new SubframeHeader(
            type,
            order,
            wastedBits
        );
    }

    private static SubframeType parseType(int bits) throws AudioFormatException {
        if (bits == 0) {
            return SubframeType.CONSTANT;
        } else if (bits == 1) {
            return SubframeType.VERBATIM;
        } else if (bits >= 8 && bits <= 12) {
            return SubframeType.FIXED;
        } else if (bits >= 32 && bits <= 63) {
            return SubframeType.LPC;
        } else {
            throw new AudioFormatException("Invalid subframe type");
        }
    }

    private static int parseOrder(int bits) {
        if (bits >= 8 && bits <= 12) {
            return bits - 8;
        } else if (bits >= 32 && bits <= 63) {
            return bits - 31;
        } else {
            return 0;
        }
    }

    SubframeType type() {
        return type;
    }

    int order() {
        return order;
    }

    int wastedBits() {
        return wastedBits;
    }

}
