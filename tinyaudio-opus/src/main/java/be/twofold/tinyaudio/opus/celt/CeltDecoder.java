package be.twofold.tinyaudio.opus.celt;

import java.util.Objects;

import static be.twofold.tinyaudio.opus.celt.Utils.celt_assert;

public final class CeltDecoder {
    private static final byte[] tapset_icdf = {2, 1, 0};

    private final CeltMode mode;
    private int rng;

    private final float[] oldBandE;

    public CeltDecoder(CeltMode mode, int channels) {
        this.mode = Objects.requireNonNull(mode);
        this.oldBandE = new float[2 * mode.nbEBands];
    }

    public int rng() {
        return rng;
    }

    public int celt_decode_with_ec(byte[] data, int dataOff, int len, float[] pcm, int pcmOff, int frame_size, int C, int end) {
        RangeDecoder dec = new RangeDecoder(data, dataOff, len);
        int LM;
        for (LM = 0; LM <= mode.maxLM; LM++) {
            if (mode.shortMdctSize << LM == frame_size) {
                break;
            }
        }
        celt_assert(LM <= mode.maxLM);
        celt_decode_test(dec, len, LM, C, end);
        rng = dec.rng();
        return frame_size;
    }

    int celt_decode_test(RangeDecoder dec, int len, int LM, int C, int end) {
        int total_bits = len * 8;
        int tell = dec.ec_tell();

        int silence;
        if (tell >= total_bits) {
            silence = 1;
        } else if (tell == 1) {
            silence = dec.ec_dec_bit_logp(15);
        } else {
            silence = 0;
        }
        if (silence != 0) {
            tell = len * 8;
            dec.nbits_total += tell - dec.ec_tell();
        }

        float postfilter_gain = 0;
        int postfilter_pitch = 0;
        int postfilter_tapset = 0;
        if (tell + 16 <= total_bits) {
            if (dec.ec_dec_bit_logp(1) != 0) {
                int qg, octave;
                octave = dec.ec_dec_uint(6);
                postfilter_pitch = (16 << octave) + dec.ec_dec_bits(4 + octave) - 1;
                qg = dec.ec_dec_bits(3);
                if (dec.ec_tell() + 2 <= total_bits) {
                    postfilter_tapset = dec.ec_dec_icdf(tapset_icdf, 2);
                }
                postfilter_gain = 0.09375f * (qg + 1);
            }
            tell = dec.ec_tell();
        }

        int isTransient;
        if (LM > 0 && tell + 3 <= total_bits) {
            isTransient = dec.ec_dec_bit_logp(3);
            tell = dec.ec_tell();
        } else {
            isTransient = 0;
        }

        int shortBlocks;
        if (isTransient != 0) {
            // shortBlocks = M;
        } else {
            shortBlocks = 0;
        }

        // Decode the global flags (first symbols in the stream)
        int intra_ener = tell + 3 <= total_bits ? dec.ec_dec_bit_logp(3) : 0;
        // Get band energies
        QuantBands.unquant_coarse_energy(mode, end, oldBandE, intra_ener, dec, C, LM);
        throw new UnsupportedOperationException();
    }
}
