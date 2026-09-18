package be.twofold.tinyvorbis;

import java.io.IOException;

record Residue(
    int type,
    int begin,
    int end,
    int partitionSize,
    int classifications,
    int classbook,
    int[] cascade,
    int[][] books
) {
    static Residue read(BitInputStream bits) throws IOException {
        int type = bits.read(16);
        if (type > 2) {
            throw new VorbisException("Invalid residue type: " + type);
        }

        int begin = bits.read(24);
        int end = bits.read(24);
        int partitionSize = bits.read(24) + 1;
        int classifications = bits.read(6) + 1;
        int classbook = bits.read(8);

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
                    books[i][j] = bits.read(8);
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
            classifications,
            classbook,
            cascade,
            books
        );
    }

    void write(BitOutputStream bits) throws IOException {
        bits.write(16, type);
        bits.write(24, begin);
        bits.write(24, end);
        bits.write(24, partitionSize - 1);
        bits.write(6, classifications - 1);
        bits.write(8, classbook);

        for (int c : cascade) {
            bits.write(3, c);
            boolean hasHigh = c > 7;
            bits.writeBool(hasHigh);
            if (hasHigh) {
                bits.write(5, c >> 3);
            }
        }

        for (int i = 0; i < classifications; i++) {
            for (int j = 0; j < 8; j++) {
                if ((cascade[i] & (1 << j)) != 0) {
                    bits.write(8, books[i][j]);
                }
            }
        }
    }
}
