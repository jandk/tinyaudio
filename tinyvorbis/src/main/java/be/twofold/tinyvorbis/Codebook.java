package be.twofold.tinyvorbis;

import java.io.IOException;
import java.util.Arrays;

record Codebook(
    int dimensions,
    int entries,
    boolean ordered,
    boolean sparse,
    int[] codewordLengths,
    int lookupType,
    int minimumValue,
    int deltaValue,
    int valueBits,
    boolean sequenceP,
    int[] multiplicands
) {
    public static final int MAGIC = 0x56_4342;

    static Codebook unpack(BitInputStream bits) throws IOException {
        if (bits.read(24) != MAGIC) {
            throw new VorbisException("Invalid Vorbis codebook");
        }

        int dimensions = bits.read(16);
        if (dimensions == 0) {
            throw new VorbisException("Codebook dimensions must be greater than zero");
        }
        int entries = bits.read(24);
        if (entries == 0) {
            throw new VorbisException("Codebook entries must be greater than zero");
        }

        boolean ordered = bits.readBool();

        boolean sparse = false;
        // TODO: Validate array length once we have BitReader that wraps byte[]
        int[] codewordLengths = new int[entries];
        if (ordered) {
            readOrdered(bits, codewordLengths);
        } else {
            sparse = bits.readBool();

            for (int i = 0; i < entries; i++) {
                if (!sparse || bits.readBool()) {
                    int length = bits.read(5);
                    codewordLengths[i] = length + 1;
                }
            }
        }

        int minimumValue = 0;
        int deltaValue = 0;
        int valueBits = 0;
        boolean sequenceP = false;
        int[] multiplicands = null;

        int lookupType = bits.read(4);
        if (lookupType == 0) {
            // do nothing
        } else if (lookupType == 1 || lookupType == 2) {
            minimumValue = bits.read(32);
            deltaValue = bits.read(32);
            valueBits = bits.read(4) + 1;
            sequenceP = bits.readBool();

            // TODO: Validate array length once we have BitReader that wraps byte[]
            int lookupValues = lookupType == 1
                ? lookup1Values(entries, dimensions)
                : Math.toIntExact((long) entries * (long) dimensions);

            multiplicands = new int[lookupValues];
            for (int i = 0; i < lookupValues; i++) {
                multiplicands[i] = bits.read(valueBits);
            }
        } else {
            throw new VorbisException("Reserved codebook lookup type: " + lookupType);
        }
        return new Codebook(
            dimensions,
            entries,
            ordered,
            sparse,
            codewordLengths,
            lookupType,
            minimumValue,
            deltaValue,
            valueBits,
            sequenceP,
            multiplicands
        );
    }

    static void readOrdered(BitInputStream bits, int[] codewordLengths) throws IOException {
        int entries = codewordLengths.length;
        int length = bits.read(5) + 1;

        int entry = 0;
        while (entry < entries) {
            if (length > 32) {
                throw new VorbisException("Ordered codebook codeword length exceeds 32 bits");
            }
            int number = bits.read(Util.ilog(entries - entry));
            if (entry + number > entries) {
                throw new VorbisException("Codebook has more codewords than its " + entries + " entries");
            }
            Arrays.fill(codewordLengths, entry, entry + number, length);
            entry += number;
            length++;
        }
    }

    static int lookup1Values(int entries, int dimensions) throws VorbisException {
        if (entries < 1 || dimensions < 1) {
            throw new VorbisException("Invalid codebook: entries=" + entries + ", dimensions=" + dimensions);
        }

        int lo = 1;
        int hi = entries;
        while (lo < hi) {
            int mid = lo + (hi - lo + 1) / 2;
            if (powAtMost(mid, dimensions, entries)) {
                lo = mid;
            } else {
                hi = mid - 1;
            }
        }
        return lo;
    }

    static boolean powAtMost(int base, int exponent, int limit) {
        long acc = 1;
        for (int i = 0; i < exponent; i++) {
            acc *= base;
            if (acc > limit) {
                return false;
            }
        }
        return true;
    }

    public float minimum() {
        return Util.float32Unpack(minimumValue);
    }

    public float delta() {
        return Util.float32Unpack(deltaValue);
    }

    void pack(BitOutputStream bits) throws IOException {
        bits.write(24, MAGIC);
        bits.write(16, dimensions);
        bits.write(24, entries);

        bits.writeBool(ordered);
        if (ordered) {
            writeOrdered(bits, codewordLengths);
        } else {
            bits.writeBool(sparse);
            for (int codewordLength : codewordLengths) {
                boolean used = codewordLength != 0;
                if (sparse) {
                    bits.writeBool(used);
                }
                if (used) {
                    bits.write(5, codewordLength - 1);
                }
            }
        }

        bits.write(4, lookupType);
        if (lookupType == 1 || lookupType == 2) {
            bits.write(32, minimumValue);
            bits.write(32, deltaValue);
            bits.write(4, valueBits - 1);
            bits.writeBool(sequenceP);

            for (int multiplicand : multiplicands) {
                bits.write(valueBits, multiplicand);
            }
        }
    }

    private void writeOrdered(BitOutputStream bits, int[] codewordLengths) throws IOException {
        int entries = codewordLengths.length;
        for (int i = 0; i < entries; i++) {
            if (codewordLengths[i] == 0 || (i > 0 && codewordLengths[i] < codewordLengths[i - 1])) {
                throw new VorbisException("Ordered codebooks require non-decreasing, non-zero codeword lengths");
            }
        }

        int length = entries == 0 ? 1 : codewordLengths[0];
        bits.write(5, length - 1);

        int entry = 0;
        while (entry < entries) {
            int number = 0;
            while (entry + number < entries && codewordLengths[entry + number] == length) {
                number++;
            }
            bits.write(Util.ilog(entries - entry), number);
            entry += number;
            length++;
        }
    }
}
