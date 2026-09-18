package be.twofold.tinyvorbis;

import java.io.IOException;

record Floor1(
    int[] partitionClassList,
    int[] classDimensions,
    int[] classSubclasses,
    int[] classMasterbooks,
    int[][] subclassBooks,
    int multiplier,
    int[] xList
) implements Floor {
    static Floor1 read(BitInputStream bits) throws IOException {
        int partitions = bits.read(5);

        int maximumClass = -1;
        int[] partitionClassList = new int[partitions];
        for (int i = 0; i < partitions; i++) {
            int partitionClass = bits.read(4);
            maximumClass = Math.max(maximumClass, partitionClass);
            partitionClassList[i] = partitionClass;
        }

        int[] classDimensions = new int[maximumClass + 1];
        int[] classSubclasses = new int[maximumClass + 1];
        int[] classMasterbooks = new int[maximumClass + 1];
        int[][] subclassBooks = new int[maximumClass + 1][];

        for (int i = 0; i < maximumClass + 1; i++) {
            classDimensions[i] = bits.read(3) + 1;
            int classSubclass = bits.read(2);
            classSubclasses[i] = classSubclass;
            if (classSubclass != 0) {
                classMasterbooks[i] = bits.read(8);
            }
            subclassBooks[i] = new int[1 << classSubclass];
            for (int j = 0; j < 1 << classSubclass; j++) {
                subclassBooks[i][j] = bits.read(8) - 1;
            }
        }

        int multiplier = bits.read(2) + 1;
        int rangebits = bits.read(4);

        int floorValues = 0;
        for (int i = 0; i < partitions; i++) {
            floorValues += classDimensions[partitionClassList[i]];
        }
        if (floorValues > 63) {
            throw new VorbisException("Floor 1 has too many values: " + floorValues);
        }

        int[] xList = new int[floorValues + 2];
        xList[0] = 0;
        xList[1] = 1 << rangebits;

        int n = 2;
        for (int i = 0; i < partitions; i++) {
            int dimensions = classDimensions[partitionClassList[i]];
            for (int j = 0; j < dimensions; j++) {
                xList[n++] = bits.read(rangebits);
            }
        }

        return new Floor1(
            partitionClassList,
            classDimensions,
            classSubclasses,
            classMasterbooks,
            subclassBooks,
            multiplier,
            xList
        );
    }

    public void write0(BitOutputStream bits) throws IOException {
        bits.write(5, partitionClassList.length);
        for (int partitionClass : partitionClassList) {
            bits.write(4, partitionClass);
        }

        int numClasses = classDimensions.length;
        for (int i = 0; i < numClasses; i++) {
            bits.write(3, classDimensions[i] - 1);
            int classSubclass = classSubclasses[i];
            bits.write(2, classSubclass);
            if (classSubclass != 0) {
                bits.write(8, classMasterbooks[i]);
            }
            for (int j = 0; j < 1 << classSubclass; j++) {
                bits.write(8, subclassBooks[i][j] + 1);
            }
        }

        int rangebits = Integer.numberOfTrailingZeros(xList[1]);
        bits.write(2, multiplier - 1);
        bits.write(4, rangebits);

        for (int i = 2; i < xList.length; i++) {
            bits.write(rangebits, xList[i]);
        }
    }
}
