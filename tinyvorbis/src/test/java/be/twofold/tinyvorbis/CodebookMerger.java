package be.twofold.tinyvorbis;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
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
        var codebooks = new LinkedHashMap<Bytes, Integer>();
        var libraries = new LinkedHashMap<String, int[]>();
        for (var codebook : CODEBOOKS) {
            var packedLibrary = readPackedLibrary(codebook.getValue());
            var indices = new int[packedLibrary.size()];
            for (int i = 0; i < packedLibrary.size(); i++) {
                var packedCodebook = packedLibrary.get(i);
                codebooks.putIfAbsent(packedCodebook, codebooks.size());
                indices[i] = codebooks.get(packedCodebook);
            }

            libraries.put(codebook.getKey(), indices);
        }

        var bytes = new ByteArrayOutputStream();
        var out = new DataOutputStream(bytes);
        out.writeBytes("CDBK");
        out.writeShort(1 << 8 | 0);
        out.writeShort(libraries.size());
        out.writeShort(codebooks.size());
        for (var library : libraries.entrySet()) {
            out.writeUTF(library.getKey());
            out.writeShort(library.getValue().length);
            for (var index : library.getValue()) {
                out.writeShort(index);
            }
        }
        for (var packedCodebook : codebooks.keySet()) {
            var repacked = repack(packedCodebook);
            out.writeShort(repacked.getKey());
            out.write(repacked.getValue());
        }
        out.flush();

        Files.write(Path.of("tinyvorbis/src/main/resources/codebooks.bin"), bytes.toByteArray());
    }

    private static Map.Entry<Integer, byte[]> repack(Bytes packedCodebook) throws IOException {
        var codebook = readPackedCodebook(packedCodebook);
        var out = new ByteArrayOutputStream();
        var written = 0;
        try (var bits = new BitOutputStream(out, ByteOrder.LITTLE_ENDIAN)) {
            codebook.write(bits);
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
        try (var in = CodebookMerger.class.getResourceAsStream("/" + name)) {
            bytes = in.readAllBytes();
        }

        var buffer = ByteBuffer.wrap(bytes)
            .order(ByteOrder.LITTLE_ENDIAN);

        var start = buffer.getInt(buffer.limit() - 4);
        var count = (buffer.limit() - start - 4) / 4;

        var offsets = new int[count + 1];
        buffer.slice(start, buffer.limit() - start)
            .order(ByteOrder.LITTLE_ENDIAN)
            .asIntBuffer().get(offsets);

        List<Bytes> codebooks = new ArrayList<>();
        for (var i = 0; i < offsets.length - 1; i++) {
            var lo = offsets[i];
            var hi = offsets[i + 1];

            var codebook = new byte[hi - lo];
            buffer.position(lo).get(codebook);
            codebooks.add(new Bytes(codebook));
        }
        return codebooks;
    }

    private static Codebook readPackedCodebook(Bytes bytes) throws IOException {
        var bits = new BitInputStream(new ByteArrayInputStream(bytes.array), ByteOrder.LITTLE_ENDIAN);
        var dimensions = bits.read(4);
        var entries = bits.read(14);

        var codewordLengths = new int[entries];
        var ordered = bits.readBool();
        var sparse = false;
        if (ordered) {
            // Whoo, it's the same!
            Codebook.readOrdered(bits, codewordLengths);
        } else {
            var codewordLengthLength = bits.read(3);
            sparse = bits.readBool();
            if (codewordLengthLength < 1 || codewordLengthLength > 5) {
                throw new VorbisException("Invalid packed codeword length length: " + codewordLengthLength);
            }

            for (var i = 0; i < entries; i++) {
                if (!sparse || bits.readBool()) {
                    codewordLengths[i] = bits.read(codewordLengthLength) + 1;
                }
            }
        }

        float minimumValue = 0;
        float deltaValue = 0;
        var valueBits = 0;
        var sequenceP = false;
        int[] multiplicands = null;

        // A single bit, so lookup types 2 and up cannot occur
        var lookupType = bits.read(1);
        if (lookupType == 1) {
            minimumValue = Util.float32Unpack(bits.read(32));
            deltaValue = Util.float32Unpack(bits.read(32));
            valueBits = bits.read(4) + 1;
            sequenceP = bits.readBool();

            var lookupValues = Codebook.lookup1Values(entries, dimensions);
            multiplicands = new int[lookupValues];
            for (var i = 0; i < lookupValues; i++) {
                multiplicands[i] = bits.read(valueBits);
            }
        }

        // Weird, end on a byte, write another one...
        var used = bits.getBitsRead() / 8 + 1;
        if (used != bytes.array.length) {
            throw new VorbisException("Packed codebook used " + used + " of " + bytes.array.length + " bytes");
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

    private record Bytes(byte[] array) {
        @Override
        public boolean equals(Object o) {
            return o instanceof Bytes bytes
                && Arrays.equals(array, bytes.array);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(array);
        }
    }
}
