package be.twofold.tinyaudio.vorbis;

import be.twofold.tinyaudio.core.internal.BitReader;

import java.io.IOException;
import java.util.Arrays;

final class Codebook {
    private static final int MAGIC = 0x56_4342;
    private final int dimensions;
    private final boolean ordered;
    private final boolean sparse;
    private final int[] codewordLengths;
    private final int lookupType;
    private final int minimumValue;
    private final int deltaValue;
    private final int valueBits;
    private final boolean sequenceP;
    private final int[] multiplicands;
    private final long[] words;
    private final float[] vectors;

    Codebook(
        int dimensions,
        boolean ordered,
        boolean sparse,
        int[] codewordLengths,
        int lookupType,
        int minimumValue,
        int deltaValue,
        int valueBits,
        boolean sequenceP,
        int[] multiplicands
    ) throws VorbisException {
        this.dimensions = dimensions;
        this.ordered = ordered;
        this.sparse = sparse;
        this.codewordLengths = codewordLengths;
        this.lookupType = lookupType;
        this.minimumValue = minimumValue;
        this.deltaValue = deltaValue;
        this.valueBits = valueBits;
        this.sequenceP = sequenceP;
        this.multiplicands = multiplicands;

        this.words = makeWords();
        this.vectors = makeVectors();
    }

    static Codebook unpack(BitReader bits) throws IOException {
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
            unpackOrdered(bits, codewordLengths);
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
            dimensions, ordered, sparse, codewordLengths, lookupType,
            minimumValue, deltaValue, valueBits, sequenceP, multiplicands
        );
    }

    static void unpackOrdered(BitReader bits, int[] codewordLengths) throws IOException {
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

    int dimensions() {
        return dimensions;
    }

    int[] codewordLengths() {
        return codewordLengths;
    }

    int lookupType() {
        return lookupType;
    }

    float[] vectors() {
        return vectors;
    }

    int decode(BitReader bits) {
        if (words.length == 0) {
            return -1;
        }
        int peek = Integer.reverse(bits.peek(32));             // first stream bit -> bit 31
        long key = (peek ^ 0x8000_0000L) << 32 | 0xFFFF_FFFFL;

        int index = -Arrays.binarySearch(words, key) - 2;
        long word = words[index];
        bits.skip((int) word & 0xFF);
        return bits.isOverrun() ? -1 : (int) word >>> 8;
    }

    private long[] makeWords() throws VorbisException {
        int usedEntries = 0;
        for (int length : codewordLengths) {
            if (length > 0) {
                usedEntries++;
            }
        }

        int[] marker = new int[33];
        long[] words = new long[usedEntries];

        int count = 0;
        for (int i = 0; i < codewordLengths.length; i++) {
            int length = codewordLengths[i];
            if (length > 0) {
                int code = marker[length];

                if (length < 32 && code >>> length != 0) {
                    throw new VorbisException("Overpopulated tree");
                }
                words[count++] = (((long) code << (32 - length) ^ 0x8000_0000L) << 32) | (long) i << 8 | length;

                for (int j = length; j > 0; j--) {
                    if ((marker[j] & 1) != 0) {
                        if (j == 1) {
                            marker[1]++;
                        } else {
                            marker[j] = marker[j - 1] << 1;
                        }
                        break;
                    }
                    marker[j]++;
                }

                for (int j = length + 1; j < 33; j++) {
                    if (marker[j] >>> 1 == code) {
                        code = marker[j];
                        marker[j] = marker[j - 1] << 1;
                    } else {
                        break;
                    }
                }
            }
        }

        if (usedEntries != 1 || marker[2] != 2) {
            for (int i = 1; i < 33; i++) {
                if ((marker[i] & (0xFFFF_FFFF >>> (32 - i))) != 0) {
                    throw new VorbisException("Underpopulated tree");
                }
            }
        }

        Arrays.sort(words);
        return words;
    }

    private float[] makeVectors() {
        if (lookupType == 0) {
            return null;
        }

        float minimum = Util.float32Unpack(minimumValue);
        float delta = Util.float32Unpack(deltaValue);
        float[] values = new float[dimensions * codewordLengths.length];
        for (int e = 0; e < codewordLengths.length; e++) {
            float last = 0;
            int divisor = 1;
            for (int i = 0; i < dimensions; i++) {
                int m;
                if (lookupType == 1) {
                    m = multiplicands[(e / divisor) % multiplicands.length];
                    divisor *= multiplicands.length;
                } else {
                    m = multiplicands[e * dimensions + i];
                }
                float value = m * delta + minimum + last;
                if (sequenceP) {
                    last = value;
                }
                values[e * dimensions + i] = value;
            }
        }
        return values;
    }

    @Override
    public String toString() {
        return "Codebook(" +
            "multiplicands=" + Arrays.toString(multiplicands) + ", " +
            "sequenceP=" + sequenceP + ", " +
            "valueBits=" + valueBits + ", " +
            "deltaValue=" + deltaValue + ", " +
            "minimumValue=" + minimumValue + ", " +
            "lookupType=" + lookupType + ", " +
            "codewordLengths=" + Arrays.toString(codewordLengths) + ", " +
            "sparse=" + sparse + ", " +
            "ordered=" + ordered + ", " +
            "dimensions=" + dimensions +
            ")";
    }
}
