package be.twofold.tinyaudio.vorbis.internal;

import be.twofold.tinyaudio.core.internal.BitReader;
import be.twofold.tinyaudio.vorbis.VorbisException;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

final class Residue {
    private final int type;
    private final int begin;
    private final int end;
    private final int partitionSize;
    private final int classBook;
    private final int[][] books;

    Residue(
        int type,
        int begin,
        int end,
        int partitionSize,
        int classBook,
        int[][] books
    ) {
        this.type = type;
        this.begin = begin;
        this.end = end;
        this.partitionSize = partitionSize;
        this.classBook = classBook;
        this.books = books;
    }

    static Residue unpack(BitReader bits, List<Codebook> codebooks) throws IOException {
        int type = bits.read(16);
        if (type > 2) {
            throw new VorbisException("Invalid residue type: " + type);
        }

        int begin = bits.read(24);
        int end = bits.read(24);
        if (begin > end) {
            throw new VorbisException("Residue begin " + begin + " is greater than end " + end);
        }

        int partitionSize = bits.read(24) + 1;
        int classifications = bits.read(6) + 1;
        int classBook = bits.read(8);
        if (classBook >= codebooks.size()) {
            throw new VorbisException("Residue class book out of range: " + classBook);
        }
        Codebook book = codebooks.get(classBook);
        if (!Codebook.powAtMost(classifications, book.dimensions(), book.codewordLengths().length)) {
            throw new VorbisException("Residue class book cannot encode " + classifications + " classifications");
        }

        int[] cascade = new int[classifications];
        for (int i = 0; i < classifications; i++) {
            int highBits = 0;
            int lowBits = bits.read(3);
            boolean bitflag = bits.readBool();
            if (bitflag) {
                highBits = bits.read(5);
            }
            cascade[i] = highBits * 8 + lowBits;
        }

        int[][] books = new int[classifications][8];
        for (int i = 0; i < classifications; i++) {
            for (int j = 0; j < 8; j++) {
                if ((cascade[i] & (1 << j)) != 0) {
                    int number = bits.read(8);
                    if (number >= codebooks.size()) {
                        throw new VorbisException("Residue book out of range: " + number);
                    }
                    if (codebooks.get(number).lookupType() == 0) {
                        throw new VorbisException("Residue book " + number + " has no value mapping");
                    }
                    books[i][j] = number;
                } else {
                    books[i][j] = -1;
                }
            }
        }

        return new Residue(
            type,
            begin,
            end,
            partitionSize,
            classBook,
            books
        );
    }

    int classifications() {
        return books.length;
    }

    public void decode(BitReader bits, List<Codebook> codebooks, int n2, float[][] pcmBundle, boolean[] zeroBundle, int count) {
        for (int i = 0; i < count; i++) {
            Arrays.fill(pcmBundle[i], 0, n2, 0.0f);
        }

        int actualSize = n2;
        int vectorCount = count;

        if (type == 2) {
            if (allSkipped(zeroBundle, count)) {
                return;
            }
            actualSize = n2 * count;
            vectorCount = 1;
        }

        int limitBegin = Math.min(begin, actualSize);
        int limitEnd = Math.min(end, actualSize);
        int toRead = limitEnd - limitBegin;
        if (toRead <= 0) {
            return;
        }

        Codebook classBook = codebooks.get(this.classBook);
        int classWords = classBook.dimensions();
        int partitionsToRead = toRead / partitionSize;
        int[][] classes = new int[vectorCount][partitionsToRead + classWords - 1];

        for (int pass = 0; pass < 8; pass++) {
            int partition = 0;
            while (partition < partitionsToRead) {
                if (pass == 0) {
                    for (int j = 0; j < vectorCount; j++) {
                        if (type != 2 && zeroBundle[j]) {
                            continue;
                        }
                        int temp = classBook.decode(bits);
                        if (temp < 0) {
                            return;
                        }
                        for (int i = classWords - 1; i >= 0; i--) {
                            classes[j][partition + i] = temp % classifications();
                            temp /= classifications();
                        }
                    }
                }
                for (int i = 0; i < classWords && partition < partitionsToRead; i++, partition++) {
                    for (int j = 0; j < vectorCount; j++) {
                        if (type != 2 && zeroBundle[j]) {
                            continue;
                        }
                        int book = books[classes[j][partition]][pass];
                        if (book >= 0) {
                            int offset = limitBegin + partition * partitionSize;
                            if (!decodePartition(bits, codebooks.get(book), pcmBundle, j, count, offset)) {
                                return;
                            }
                        }
                    }
                }
            }
        }
    }

    private boolean allSkipped(boolean[] skip, int count) {
        for (int i = 0; i < count; i++) {
            if (!skip[i]) {
                return false;
            }
        }
        return true;
    }

    private boolean decodePartition(BitReader bits, Codebook codebook, float[][] pcmBundle, int j, int count, int offset) {
        int dim = codebook.dimensions();
        float[] values = codebook.vectors();
        int rows = partitionSize / dim;

        for (int r = 0; r < rows; r++) {
            int entry = codebook.decode(bits);
            if (entry < 0) {
                return false;
            }
            int base = entry * dim;
            for (int d = 0; d < dim; d++) {
                float value = values[base + d];
                switch (type) {
                    case 0:
                        pcmBundle[j][offset + r + d * rows] += value;
                        break;
                    case 1:
                        pcmBundle[j][offset + r * dim + d] += value;
                        break;
                    default:
                        int t = offset + r * dim + d;
                        pcmBundle[t % count][t / count] += value;
                        break;
                }
            }
        }
        return true;
    }
}
