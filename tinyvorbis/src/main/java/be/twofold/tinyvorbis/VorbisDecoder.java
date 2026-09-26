package be.twofold.tinyvorbis;

import be.twofold.tinyaudio.core.Packet;
import be.twofold.tinyaudio.core.PacketSource;

import java.io.IOException;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Objects;

public final class VorbisDecoder {
    private static final byte[] VORBIS = {'v', 'o', 'r', 'b', 'i', 's'};

    private final PacketSource packets;
    private final Identification identification;
    private final SetupHeader setup;
    private final float[][] previous;

    // Floor
    private final int[][] floorY;
    private final boolean[] noResidue;

    public VorbisDecoder(PacketSource packets) throws IOException {
        this.packets = Objects.requireNonNull(packets);
        this.identification = Identification.read(header(1));
        header(3);
        setup = SetupHeader.read(header(5), identification.audioChannels());
        previous = new float[identification.audioChannels()][];

        floorY = new int[identification.audioChannels()][65];
        noResidue = new boolean[identification.audioChannels()];
    }

    private BitReader header(int type) throws IOException {
        Packet packet = packets.next();
        if (packet == null) {
            throw new VorbisException("Stream ended before header packet of type " + type);
        }

        byte[] data = packet.data();
        if (data.length < 7 || data[0] != type || !Arrays.equals(data, 1, 7, VORBIS, 0, 6)) {
            throw new VorbisException("Expected header packet of type " + type + ", got " + Util.describe(data));
        }

        return new BitReader(data, 7, data.length - 7, ByteOrder.LITTLE_ENDIAN);
    }

    DecodeType decode() throws IOException {
        Packet packet = packets.next();
        if (packet == null) {
            return DecodeType.END;
        }

        var bits = new BitReader(packet.data(), ByteOrder.LITTLE_ENDIAN);
        if (bits.read(1) != 0) {
            return DecodeType.BAD;
        }

        int modeCountLog2 = Util.ilog(setup.modes().size() - 1);
        int modeNumber = bits.read(modeCountLog2);
        if (modeNumber >= setup.modes().size()) {
            return DecodeType.BAD;
        }
        Mode mode = setup.modes().get(modeNumber);

        int n;
        boolean previousWindowFlag = false;
        boolean nextWindowFlag = false;
        if (mode.blockFlag()) {
            n = identification.blockSize1();
            previousWindowFlag = bits.readBool();
            nextWindowFlag = bits.readBool();
        } else {
            n = identification.blockSize0();
        }
        if (bits.isOverrun()) {
            return DecodeType.BAD;
        }

        int windowCenter = n / 2;
        int leftWindowStart;
        int leftWindowEnd;
        int leftN;
        if (mode.blockFlag() && !previousWindowFlag) {
            leftWindowStart = n / 4 - identification.blockSize0() / 4;
            leftWindowEnd = n / 4 + identification.blockSize0() / 4;
            leftN = identification.blockSize0() / 2;
        } else {
            leftWindowStart = 0;
            leftWindowEnd = windowCenter;
            leftN = n / 2;
        }


        int rightWindowStart;
        int rightWindowEnd;
        int rightN;
        if (mode.blockFlag() && !nextWindowFlag) {
            rightWindowStart = n * 3 / 4 - identification.blockSize0() / 4;
            rightWindowEnd = n * 3 / 4 + identification.blockSize0() / 4;
            rightN = identification.blockSize0() / 2;
        } else {
            rightWindowStart = windowCenter;
            rightWindowEnd = n;
            rightN = n / 2;
        }

        // 4.3.2 floor curve decode
        Mapping mapping = setup.mappings().get(mode.mapping());
        for (int ch = 0; ch < identification.audioChannels(); ch++) {
            int submap = mapping.mux()[ch];
            Floor floor = setup.floors().get(mapping.submapFloor()[submap]);
            noResidue[ch] = floor.decode(bits, setup.codebooks(), floorY[ch]);
        }

        // 4.3.3 nonzero vector propagate
        for (int i = 0; i < mapping.couplingSteps(); i++) {
            int mag = mapping.magnitude()[i];
            int ang = mapping.angle()[i];
            if (!noResidue[mag] || !noResidue[ang]) {
                noResidue[mag] = noResidue[ang] = false;
            }
        }

        System.out.println(leftWindowStart + "\t" + leftWindowEnd + "\t" + leftN + "\t" + rightWindowStart + "\t" + rightWindowEnd + "\t" + rightN);
        return DecodeType.GOOD;
    }

    enum DecodeType {
        GOOD,
        BAD,
        END,
    }
}
