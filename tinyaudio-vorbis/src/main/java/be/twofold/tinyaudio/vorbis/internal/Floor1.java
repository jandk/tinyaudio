package be.twofold.tinyaudio.vorbis.internal;

import be.twofold.tinyaudio.core.internal.BitReader;
import be.twofold.tinyaudio.core.internal.MathUtils;
import be.twofold.tinyaudio.vorbis.VorbisException;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

final class Floor1 implements Floor {
    private static final int[] RANGES = {256, 128, 86, 64};

    private static final float[] INVERSE_DB_TABLE = {
        1.0649863e-7f, 1.1341951e-7f, 1.2079015e-7f, 1.2863978e-7f,
        1.3699950e-7f, 1.4590250e-7f, 1.5538409e-7f, 1.6548181e-7f,
        1.7623574e-7f, 1.8768856e-7f, 1.9988560e-7f, 2.1287531e-7f,
        2.2670913e-7f, 2.4144197e-7f, 2.5713223e-7f, 2.7384212e-7f,
        2.9163792e-7f, 3.1059022e-7f, 3.3077410e-7f, 3.5226967e-7f,
        3.7516213e-7f, 3.9954230e-7f, 4.2550681e-7f, 4.5315863e-7f,
        4.8260745e-7f, 5.1397001e-7f, 5.4737063e-7f, 5.8294188e-7f,
        6.2082472e-7f, 6.6116939e-7f, 7.0413591e-7f, 7.4989464e-7f,
        7.9862701e-7f, 8.5052631e-7f, 9.0579829e-7f, 9.6466215e-7f,
        1.0273513e-6f, 1.0941144e-6f, 1.1652161e-6f, 1.2409384e-6f,
        1.3215816e-6f, 1.4074654e-6f, 1.4989305e-6f, 1.5963394e-6f,
        1.7000785e-6f, 1.8105592e-6f, 1.9282195e-6f, 2.0535260e-6f,
        2.1869757e-6f, 2.3290977e-6f, 2.4804558e-6f, 2.6416496e-6f,
        2.8133190e-6f, 2.9961443e-6f, 3.1908505e-6f, 3.3982101e-6f,
        3.6190449e-6f, 3.8542307e-6f, 4.1047006e-6f, 4.3714472e-6f,
        4.6555283e-6f, 4.9580708e-6f, 5.2802739e-6f, 5.6234162e-6f,
        5.9888571e-6f, 6.3780467e-6f, 6.7925284e-6f, 7.2339453e-6f,
        7.7040477e-6f, 8.2047000e-6f, 8.7378876e-6f, 9.3057251e-6f,
        9.9104636e-6f, 1.0554501e-5f, 1.1240392e-5f, 1.1970856e-5f,
        1.2748789e-5f, 1.3577278e-5f, 1.4459606e-5f, 1.5399271e-5f,
        1.6400005e-5f, 1.7465769e-5f, 1.8600793e-5f, 1.9809577e-5f,
        2.1096914e-5f, 2.2467912e-5f, 2.3928002e-5f, 2.5482977e-5f,
        2.7139005e-5f, 2.8902650e-5f, 3.0780910e-5f, 3.2781227e-5f,
        3.4911533e-5f, 3.7180282e-5f, 3.9596467e-5f, 4.2169668e-5f,
        4.4910092e-5f, 4.7828602e-5f, 5.0936775e-5f, 5.4246932e-5f,
        5.7772202e-5f, 6.1526567e-5f, 6.5524910e-5f, 6.9783084e-5f,
        7.4317984e-5f, 7.9147583e-5f, 8.4291038e-5f, 8.9768750e-5f,
        9.5602423e-5f, 1.0181521e-4f, 1.0843174e-4f, 1.1547824e-4f,
        1.2298267e-4f, 1.3097477e-4f, 1.3948625e-4f, 1.4855085e-4f,
        1.5820454e-4f, 1.6848555e-4f, 1.7943469e-4f, 1.9109536e-4f,
        2.0351382e-4f, 2.1673930e-4f, 2.3082423e-4f, 2.4582449e-4f,
        2.6179955e-4f, 2.7881275e-4f, 2.9693157e-4f, 3.1622787e-4f,
        3.3677815e-4f, 3.5866388e-4f, 3.8197188e-4f, 4.0679457e-4f,
        4.3323037e-4f, 4.6138410e-4f, 4.9136748e-4f, 5.2329927e-4f,
        5.5730622e-4f, 5.9352309e-4f, 6.3209358e-4f, 6.7317061e-4f,
        7.1691698e-4f, 7.6350628e-4f, 8.1312325e-4f, 8.6596457e-4f,
        9.2223985e-4f, 9.8217221e-4f, 1.0459992e-3f, 1.1139743e-3f,
        1.1863665e-3f, 1.2634633e-3f, 1.3455702e-3f, 1.4330129e-3f,
        1.5261382e-3f, 1.6253153e-3f, 1.7309374e-3f, 1.8434235e-3f,
        1.9632196e-3f, 2.0908006e-3f, 2.2266726e-3f, 2.3713743e-3f,
        2.5254795e-3f, 2.6895993e-3f, 2.8643848e-3f, 3.0505287e-3f,
        3.2487691e-3f, 3.4598925e-3f, 3.6847359e-3f, 3.9241905e-3f,
        4.1792067e-3f, 4.4507948e-3f, 4.7400328e-3f, 5.0480668e-3f,
        5.3761187e-3f, 5.7254890e-3f, 6.0975635e-3f, 6.4938175e-3f,
        6.9158226e-3f, 7.3652514e-3f, 7.8438874e-3f, 8.3536273e-3f,
        8.8964924e-3f, 9.4746370e-3f, 1.0090352e-2f, 1.0746080e-2f,
        1.1444421e-2f, 1.2188144e-2f, 1.2980198e-2f, 1.3823725e-2f,
        1.4722068e-2f, 1.5678791e-2f, 1.6697686e-2f, 1.7782796e-2f,
        1.8938422e-2f, 2.0169148e-2f, 2.1479854e-2f, 2.2875736e-2f,
        2.4362329e-2f, 2.5945531e-2f, 2.7631618e-2f, 2.9427277e-2f,
        3.1339627e-2f, 3.3376250e-2f, 3.5545226e-2f, 3.7855156e-2f,
        4.0315200e-2f, 4.2935107e-2f, 4.5725275e-2f, 4.8696756e-2f,
        5.1861349e-2f, 5.5231590e-2f, 5.8820851e-2f, 6.2643364e-2f,
        6.6714279e-2f, 7.1049750e-2f, 7.5666964e-2f, 8.0584228e-2f,
        8.5821047e-2f, 9.1398180e-2f, 9.7337745e-2f, 1.0366330e-1f,
        1.1039993e-1f, 1.1757434e-1f, 1.2521498e-1f, 1.3335215e-1f,
        1.4201812e-1f, 1.5124726e-1f, 1.6107617e-1f, 1.7154381e-1f,
        1.8269168e-1f, 1.9456401e-1f, 2.0720787e-1f, 2.2067343e-1f,
        2.3501402e-1f, 2.5028655e-1f, 2.6655158e-1f, 2.8387362e-1f,
        3.0232131e-1f, 3.2196787e-1f, 3.4289113e-1f, 3.6517414e-1f,
        3.8890520e-1f, 4.1417846e-1f, 4.4109413e-1f, 4.6975890e-1f,
        5.0028646e-1f, 5.3279793e-1f, 5.6742209e-1f, 6.0429639e-1f,
        6.4356697e-1f, 6.8538958e-1f, 7.2993004e-1f, 7.7736503e-1f,
        8.2788259e-1f, 8.8168305e-1f, 9.3897980e-1f, 1.0000000e+0f,
    };
    private final int[] partitionClassList;
    private final int[] classDimensions;
    private final int[] classMasterBooks;
    private final int[][] subclassBooks;
    private final int multiplier;
    private final int[] xList;
    private final int[] sortOrder;
    private final int[] lowNeighbors;
    private final int[] highNeighbors;

    Floor1(
        int[] partitionClassList,
        int[] classDimensions,
        int[] classMasterBooks,
        int[][] subclassBooks,
        int multiplier,
        int[] xList
    ) {
        this.partitionClassList = partitionClassList;
        this.classDimensions = classDimensions;
        this.classMasterBooks = classMasterBooks;
        this.subclassBooks = subclassBooks;
        this.multiplier = multiplier;
        this.xList = xList;

        int posts = xList.length;
        this.sortOrder = makeSortOrder();
        this.lowNeighbors = new int[posts];
        this.highNeighbors = new int[posts];
        for (int i = 2; i < posts; i++) {
            lowNeighbors[i] = lowNeighbor(xList, i);
            highNeighbors[i] = highNeighbor(xList, i);
        }
    }

    private int[] makeSortOrder() {
        int posts = xList.length;
        int[] order = new int[posts];
        for (int i = 0; i < posts; i++) {
            order[i] = i;
        }
        for (int i = 1; i < posts; i++) {
            int p = order[i];
            int j = i - 1;
            while (j >= 0 && xList[order[j]] > xList[p]) {
                order[j + 1] = order[j];
                j--;
            }
            order[j + 1] = p;
        }
        return order;
    }

    static Floor1 unpack(BitReader bits, int codebookCount) throws IOException {
        int partitions = bits.read(5);

        int maximumClass = -1;
        int[] partitionClassList = new int[partitions];
        for (int i = 0; i < partitions; i++) {
            int partitionClass = bits.read(4);
            maximumClass = Math.max(maximumClass, partitionClass);
            partitionClassList[i] = partitionClass;
        }

        int[] classDimensions = new int[maximumClass + 1];
        int[] classMasterBooks = new int[maximumClass + 1];
        int[][] subclassBooks = new int[maximumClass + 1][];

        for (int i = 0; i < maximumClass + 1; i++) {
            classDimensions[i] = bits.read(3) + 1;
            int classSubclass = bits.read(2);
            if (classSubclass != 0) {
                int book = bits.read(8);
                if (book >= codebookCount) {
                    throw new VorbisException("Floor 1 class master book out of range: " + book);
                }
                classMasterBooks[i] = book;
            } else {
                classMasterBooks[i] = -1;
            }
            subclassBooks[i] = new int[1 << classSubclass];
            for (int j = 0; j < 1 << classSubclass; j++) {
                int book = bits.read(8) - 1;
                if (book >= codebookCount) {
                    throw new VorbisException("Floor 1 subclass book out of range: " + book);
                }
                subclassBooks[i][j] = book;
            }
        }

        int multiplier = bits.read(2) + 1;
        int rangeBits = bits.read(4);

        int floorValues = 0;
        for (int i = 0; i < partitions; i++) {
            floorValues += classDimensions[partitionClassList[i]];
        }
        if (floorValues > 63) {
            throw new VorbisException("Floor 1 has too many values: " + floorValues);
        }

        int[] xList = new int[floorValues + 2];
        xList[0] = 0;
        xList[1] = 1 << rangeBits;

        int n = 2;
        for (int i = 0; i < partitions; i++) {
            int dimensions = classDimensions[partitionClassList[i]];
            for (int j = 0; j < dimensions; j++) {
                xList[n++] = bits.read(rangeBits);
            }
        }

        for (int i = 0; i < xList.length; i++) {
            for (int j = i + 1; j < xList.length; j++) {
                if (xList[i] == xList[j]) {
                    throw new VorbisException("Floor 1 X list has duplicate values: " + xList[i]);
                }
            }
        }

        return new Floor1(
            partitionClassList,
            classDimensions,
            classMasterBooks,
            subclassBooks,
            multiplier,
            xList
        );
    }

    private int classSubclasses(int classNumber) {
        return Integer.numberOfTrailingZeros(subclassBooks[classNumber].length);
    }

    @Override
    public boolean decode(BitReader bits, List<Codebook> codebooks, float[] floorY) {
        if (!bits.readBool()) {
            return true;
        }

        int range = RANGES[multiplier - 1];
        int rangeLog2 = Util.ilog(range - 1);
        floorY[0] = bits.read(rangeLog2);
        floorY[1] = bits.read(rangeLog2);

        int offset = 2;
        for (int cClass : partitionClassList) {
            int cDim = classDimensions[cClass];
            int cBits = classSubclasses(cClass);
            int cSub = (1 << cBits) - 1;
            int cVal = 0;

            if (cBits > 0) {
                cVal = codebooks.get(classMasterBooks[cClass]).decode(bits);
                if (cVal < 0) {
                    return true;
                }
            }

            for (int j = 0; j < cDim; j++) {
                int book = subclassBooks[cClass][cVal & cSub];
                cVal >>>= cBits;
                if (book >= 0) {
                    int value = codebooks.get(book).decode(bits);
                    if (value < 0) {
                        return true;
                    }
                    floorY[offset + j] = value;
                } else {
                    floorY[offset + j] = 0;
                }
            }
            offset += cDim;
        }

        return bits.isOverrun();
    }

    @Override
    public void synthesize(float[] floorY, float[] spectrum, int n2) {
        int range = RANGES[multiplier - 1];
        int posts = xList.length;

        boolean[] step2Flag = new boolean[posts];
        step2Flag[0] = true;
        step2Flag[1] = true;

        int[] finalY = new int[posts];
        finalY[0] = (int) floorY[0];
        finalY[1] = (int) floorY[1];

        for (int i = 2; i < posts; i++) {
            int lowNeighborOffset = lowNeighbors[i];
            int highNeighborOffset = highNeighbors[i];
            int predicted = renderPoint(
                xList[lowNeighborOffset], finalY[lowNeighborOffset],
                xList[highNeighborOffset], finalY[highNeighborOffset],
                xList[i]
            );
            int val = (int) floorY[i];
            int highRoom = range - predicted;
            int lowRoom = predicted;

            int room = highRoom < lowRoom ? highRoom * 2 : lowRoom * 2;
            if (val != 0) {
                step2Flag[lowNeighborOffset] = true;
                step2Flag[highNeighborOffset] = true;
                step2Flag[i] = true;

                if (val >= room) {
                    if (highRoom > lowRoom) {
                        finalY[i] = val - lowRoom + predicted;
                    } else {
                        finalY[i] = predicted - val + highRoom - 1;
                    }
                } else {
                    if (val % 2 != 0) {
                        finalY[i] = predicted - ((val + 1) >> 1);
                    } else {
                        finalY[i] = predicted + (val >> 1);
                    }
                }
            } else {
                step2Flag[i] = false;
                finalY[i] = predicted;
            }
        }

        for (int i = 0; i < posts; i++) {
            finalY[i] = MathUtils.clamp(finalY[i], 0, range - 1);
        }

        int lx = 0;
        int ly = finalY[0] * multiplier;
        int hx = 0;
        int hy = ly;
        for (int k = 1; k < posts; k++) {
            int p = sortOrder[k];
            if (step2Flag[p]) {
                hx = xList[p];
                hy = finalY[p] * multiplier;
                renderLine(spectrum, lx, ly, hx, hy, n2);
                lx = hx;
                ly = hy;
            }
        }

        if (hx < n2) {
            renderLine(spectrum, hx, hy, n2, hy, n2);
        }
    }

    private static int lowNeighbor(int[] v, int x) {
        int index = 0;
        for (int i = 1; i < x; i++) {
            if (v[i] < v[x] && v[i] > v[index]) {
                index = i;
            }
        }
        return index;
    }

    private static int highNeighbor(int[] v, int x) {
        int index = 1;
        for (int i = 2; i < x; i++) {
            if (v[i] > v[x] && v[i] < v[index]) {
                index = i;
            }
        }
        return index;
    }

    private static int renderPoint(int x0, int y0, int x1, int y1, int x) {
        int dy = y1 - y0;
        int off = Math.abs(dy) * (x - x0) / (x1 - x0);
        return dy < 0 ? y0 - off : y0 + off;
    }

    private static void renderLine(float[] v, int x0, int y0, int x1, int y1, int n) {
        int dy = y1 - y0;
        int adx = x1 - x0;
        int base = dy / adx;
        int ady = Math.abs(dy) - Math.abs(base) * adx;
        int end = Math.min(x1, n);
        int y = y0;
        int err = 0;

        int sy = dy < 0 ? base - 1 : base + 1;

        if (x0 < end) {
            v[x0] *= INVERSE_DB_TABLE[y];
        }

        for (int x = x0 + 1; x <= end - 1; x++) {
            err += ady;
            if (err >= adx) {
                err -= adx;
                y += sy;
            } else {
                y += base;
            }
            v[x] *= INVERSE_DB_TABLE[y];
        }
    }

    @Override
    public String toString() {
        return "Floor1(" +
            "partitionClassList=" + Arrays.toString(partitionClassList) + ", " +
            "classDimensions=" + Arrays.toString(classDimensions) + ", " +
            "classMasterBooks=" + Arrays.toString(classMasterBooks) + ", " +
            "subclassBooks=" + Arrays.deepToString(subclassBooks) + ", " +
            "multiplier=" + multiplier + ", " +
            "xList=" + Arrays.toString(xList) +
            ")";
    }
}
