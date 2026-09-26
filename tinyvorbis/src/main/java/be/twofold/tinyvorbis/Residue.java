package be.twofold.tinyvorbis;

import java.io.IOException;
import java.util.List;

record Residue(
    int type,
    int begin,
    int end,
    int partitionSize,
    int classBook,
    int[][] books
) {
    static Residue unpack(BitInputStream bits, List<Codebook> codebooks) throws IOException {
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
        if (!Codebook.powAtMost(classifications, book.dimensions(), book.entries())) {
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

    void pack(BitOutputStream bits) throws IOException {
        bits.write(16, type);
        bits.write(24, begin);
        bits.write(24, end);
        bits.write(24, partitionSize - 1);
        bits.write(6, classifications() - 1);
        bits.write(8, classBook);

        for (int[] classBooks : books) {
            int cascade = 0;
            for (int j = 0; j < 8; j++) {
                if (classBooks[j] != -1) {
                    cascade |= 1 << j;
                }
            }

            bits.write(3, cascade);
            boolean hasHigh = cascade > 7;
            bits.writeBool(hasHigh);
            if (hasHigh) {
                bits.write(5, cascade >> 3);
            }
        }

        for (int[] classBooks : books) {
            for (int book : classBooks) {
                if (book != -1) {
                    bits.write(8, book);
                }
            }
        }
    }
}
