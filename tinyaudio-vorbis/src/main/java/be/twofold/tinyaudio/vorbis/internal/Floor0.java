package be.twofold.tinyaudio.vorbis.internal;

import be.twofold.tinyaudio.core.AudioFormatException;
import be.twofold.tinyaudio.core.internal.BitReader;

import java.util.List;
import java.util.Objects;

final class Floor0 implements Floor {
    private final int[][] linearMaps = new int[13][];
    private final int order;
    private final int rate;
    private final int barkMapSize;
    private final int amplitudeBits;
    private final int amplitudeOffset;
    private final int[] books;

    private Floor0(
        int order,
        int rate,
        int barkMapSize,
        int amplitudeBits,
        int amplitudeOffset,
        int[] books
    ) {
        this.order = order;
        this.rate = rate;
        this.barkMapSize = barkMapSize;
        this.amplitudeBits = amplitudeBits;
        this.amplitudeOffset = amplitudeOffset;
        this.books = Objects.requireNonNull(books);
    }

    static Floor0 unpack(BitReader bits, List<Codebook> codebooks) throws AudioFormatException {
        int order = bits.read(8);
        if (order < 1) {
            throw new AudioFormatException("Floor 0 order must be at least 1");
        }
        int rate = bits.read(16);
        if (rate < 1) {
            throw new AudioFormatException("Floor 0 rate must be at least 1");
        }
        int barkMapSize = bits.read(16);
        if (barkMapSize < 1) {
            throw new AudioFormatException("Floor 0 bark map size must be at least 1");
        }
        int amplitudeBits = bits.read(6);
        if (amplitudeBits > 32) {
            throw new AudioFormatException("Floor 0 amplitude bits out of range: " + amplitudeBits);
        }
        int amplitudeOffset = bits.read(8);

        int bookCount = bits.read(4) + 1;
        int[] books = new int[bookCount];
        for (int i = 0; i < bookCount; i++) {
            int book = bits.read(8);
            if (book >= codebooks.size()) {
                throw new AudioFormatException("Floor 0 book out of range: " + book);
            }
            if (codebooks.get(book).lookupType() == 0) {
                throw new AudioFormatException("Floor 0 book " + book + " has no value mapping");
            }
            books[i] = book;
        }

        return new Floor0(order, rate, barkMapSize, amplitudeBits, amplitudeOffset, books);
    }

    @Override
    public boolean decode(BitReader bits, List<Codebook> codebooks, float[] lsp) {
        int amplitude = bits.read(amplitudeBits);
        if (amplitude <= 0 || bits.isOverrun()) {
            return true;
        }
        int bookNumber = bits.read(Util.ilog(books.length));
        if (bookNumber >= books.length || bits.isOverrun()) {
            return true;
        }

        Codebook book = codebooks.get(books[bookNumber]);
        if (!book.decodeVSet(bits, lsp, 0, order)) {
            return true;
        }

        int dim = book.dimensions();
        float last = 0.0f;
        for (int j = 0; j < order; ) {
            for (int k = 0; j < order && k < dim; k++, j++) {
                lsp[j] += last;
            }
            last = lsp[j - 1];
        }

        int maxValue = (1 << amplitudeBits) - 1;
        lsp[order] = (float) amplitude / maxValue * amplitudeOffset;
        return false;
    }

    @Override
    public void synthesize(float[] lsp, float[] spectrum, int n2) {
        int log2 = Integer.numberOfTrailingZeros(n2);
        if (linearMaps[log2] == null) {
            linearMaps[log2] = linearMap(n2);
        }
        int[] map = linearMaps[log2];

        float wdel = (float) (Math.PI / barkMapSize);
        for (int i = 0; i < order; i++) {
            lsp[i] = 2.0f * (float) Math.cos(lsp[i]);
        }

        for (int i = 0; i < n2; ) {
            int j, k = map[i];
            float p = .5f;
            float q = .5f;
            float w = 2.0f * (float) Math.cos(wdel * k);
            for (j = 1; j < order; j += 2) {
                q *= w - lsp[j - 1];
                p *= w - lsp[j];
            }
            if (j == order) {
                // odd order filter; slightly asymmetric the last coefficient
                q *= w - lsp[j - 1];
                p *= p * (4.0f - w * w);
                q *= q;
            } else {
                // even order filter; still symmetric
                p *= p * (2.0f - w);
                q *= q * (2.0f + w);
            }

            q = fromdB(lsp[order] / Math.sqrt(p + q) - (float) amplitudeOffset);

            do {
                spectrum[i] *= q;
            } while (map[++i] == k);
        }
    }

    private int[] linearMap(int n) {
        float scale = (float) (barkMapSize / toBark(rate / 2.0f));
        int[] map = new int[n + 1];
        for (int j = 0; j < n; j++) {
            int val = (int) Math.floor(toBark(rate / 2.0f / n * j) * scale);
            map[j] = Math.min(val, barkMapSize - 1);
        }
        map[n] = -1;
        return map;
    }

    private float fromdB(double x) {
        return (float) Math.exp(x * .11512925f);
    }

    private double toBark(float n) {
        return 13.1f * Math.atan(.00074f * n) + 2.24f * Math.atan(n * n * 1.85e-8f) + 1e-4f * n;
    }
}
