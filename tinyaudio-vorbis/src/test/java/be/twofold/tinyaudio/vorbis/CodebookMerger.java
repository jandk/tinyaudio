package be.twofold.tinyaudio.vorbis;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

final class CodebookMerger {
    private static final List<Map.Entry<String, String>> CODEBOOKS = List.of(
        Map.entry("default", "packed_codebooks.bin"),
        Map.entry("aoTuV_603", "packed_codebooks_aoTuV_603.bin")
    );

    public static void main(String[] args) throws IOException {
        Map<Bytes, Integer> codebooks = new LinkedHashMap<>();
        Map<String, int[]> libraries = new LinkedHashMap<>();
        for (Map.Entry<String, String> codebook : CODEBOOKS) {
            List<Bytes> packedLibrary = readPackedLibrary(codebook.getValue());
            int[] indices = new int[packedLibrary.size()];
            for (int i = 0; i < packedLibrary.size(); i++) {
                Bytes packedCodebook = packedLibrary.get(i);
                codebooks.putIfAbsent(packedCodebook, codebooks.size());
                indices[i] = codebooks.get(packedCodebook);
            }

            libraries.put(codebook.getKey(), indices);
        }

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeBytes("CDBK");
        out.writeShort(1 << 8 | 0);
        out.writeShort(libraries.size());
        out.writeShort(codebooks.size());
        for (Map.Entry<String, int[]> library : libraries.entrySet()) {
            out.writeUTF(library.getKey());
            out.writeShort(library.getValue().length);
            for (int index : library.getValue()) {
                out.writeShort(index);
            }
        }
        for (Bytes packedCodebook : codebooks.keySet()) {
            Map.Entry<Integer, byte[]> repacked = repack(packedCodebook);
            out.writeShort(repacked.getKey());
            out.write(repacked.getValue());
        }
        out.flush();

        Files.write(Path.of("tinyvorbis/src/main/resources/codebooks.bin"), bytes.toByteArray());
    }

    private static Map.Entry<Integer, byte[]> repack(Bytes packedCodebook) throws IOException {
        Codebook codebook = readPackedCodebook(packedCodebook);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int written;
        try (BitOutputStream bits = new BitOutputStream(out, ByteOrder.LITTLE_ENDIAN)) {
            codebook.pack(bits);
            bits.flush();
            written = bits.getBitsWritten();
        }
        if (written > 0xFFFF) {
            throw new VorbisException("Codebook is " + written + " bits, too long to store in a short");
        }
        return Map.entry(written, out.toByteArray());
    }

    private static List<Bytes> readPackedLibrary(String name) throws IOException {
        byte[] bytes;
        try (InputStream in = CodebookMerger.class.getResourceAsStream("/" + name)) {
            bytes = in.readAllBytes();
        }

        ByteBuffer buffer = ByteBuffer.wrap(bytes)
            .order(ByteOrder.LITTLE_ENDIAN);

        int start = buffer.getInt(buffer.limit() - 4);
        int count = (buffer.limit() - start - 4) / 4;

        int[] offsets = new int[count + 1];
        buffer.duplicate().position(start).slice()
            .order(ByteOrder.LITTLE_ENDIAN)
            .asIntBuffer().get(offsets);

        List<Bytes> codebooks = new ArrayList<>();
        for (int i = 0; i < offsets.length - 1; i++) {
            int lo = offsets[i];
            int hi = offsets[i + 1];

            byte[] codebook = new byte[hi - lo];
            buffer.position(lo).get(codebook);
            codebooks.add(new Bytes(codebook));
        }
        return codebooks;
    }

    private static Codebook readPackedCodebook(Bytes bytes) throws IOException {
        BitReader bits = new BitReader(bytes.array, ByteOrder.LITTLE_ENDIAN);
        int dimensions = bits.read(4);
        int entries = bits.read(14);

        int[] codewordLengths = new int[entries];
        boolean ordered = bits.readBool();
        boolean sparse = false;
        if (ordered) {
            // Whoo, it's the same!
            Codebook.unpackOrdered(bits, codewordLengths);
        } else {
            int codewordLengthLength = bits.read(3);
            sparse = bits.readBool();
            if (codewordLengthLength < 1 || codewordLengthLength > 5) {
                throw new VorbisException("Invalid packed codeword length length: " + codewordLengthLength);
            }

            for (int i = 0; i < entries; i++) {
                if (!sparse || bits.readBool()) {
                    codewordLengths[i] = bits.read(codewordLengthLength) + 1;
                }
            }
        }

        int minimumValue = 0;
        int deltaValue = 0;
        int valueBits = 0;
        boolean sequenceP = false;
        int[] multiplicands = null;

        // A single bit, so lookup types 2 and up cannot occur
        int lookupType = bits.read(1);
        if (lookupType == 1) {
            minimumValue = bits.read(32);
            deltaValue = bits.read(32);
            valueBits = bits.read(4) + 1;
            sequenceP = bits.readBool();

            int lookupValues = Codebook.lookup1Values(entries, dimensions);
            multiplicands = new int[lookupValues];
            for (int i = 0; i < lookupValues; i++) {
                multiplicands[i] = bits.read(valueBits);
            }
        }

        // Weird, end on a byte, write another one...
        int used = bits.bitsRead() / 8 + 1;
        if (used != bytes.array.length) {
            throw new VorbisException("Packed codebook used " + used + " of " + bytes.array.length + " bytes");
        }

        return new Codebook(
            dimensions,
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

    private static final class Bytes {
        private final byte[] array;

        private Bytes(byte[] array) {
            this.array = array;
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof Bytes
                && Arrays.equals(array, ((Bytes) obj).array);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(array);
        }

        @Override
        public String toString() {
            return "[" + array.length + " bytes]";
        }
    }
}
