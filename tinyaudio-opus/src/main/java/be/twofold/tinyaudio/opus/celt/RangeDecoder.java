package be.twofold.tinyaudio.opus.celt;

import static be.twofold.tinyaudio.opus.celt.Utils.EC_ILOG;
import static be.twofold.tinyaudio.opus.celt.Utils.celt_assert;

/**
 * A range decoder.
 * This is an entropy decoder based upon \cite{Mar79}, which is itself a
 * rediscovery of the FIFO arithmetic code introduced by \cite{Pas76}.
 * It is very similar to arithmetic encoding, except that encoding is done with
 * digits in any base, instead of with bits, and so it is faster when using
 * larger bases (i.e.: a byte).
 * The author claims an average waste of {@code 1/2 log_b(2b)} bits, where {@code b}
 * is the base, longer than the theoretical optimum, but to my knowledge there
 * is no published justification for this claim.
 * This only seems true when using near-infinite precision arithmetic so that
 * the process is carried out with no rounding errors.
 *
 * <p>An excellent description of implementation details is available at
 * <a href="http://www.arturocampos.com/ac_range.html">http://www.arturocampos.com/ac_range.html</a>.
 * A recent work \cite{MNW98} which proposes several changes to arithmetic
 * encoding for efficiency actually re-discovers many of the principles
 * behind range encoding, and presents a good theoretical analysis of them.
 *
 * <p>End of stream is handled by writing out the smallest number of bits that
 * ensures that the stream will be correctly decoded regardless of the value of
 * any subsequent bits.
 * {@code ec_tell()} can be used to determine how many bits were needed to decode
 * all the symbols thus far; other data can be packed in the remaining bits of
 * the input buffer.
 *
 * <pre>
 * {@literal @}PHDTHESIS{Pas76,
 *   author="Richard Clark Pasco",
 *   title="Source coding algorithms for fast data compression",
 *   school="Dept. of Electrical Engineering, Stanford University",
 *   address="Stanford, CA",
 *   month=May,
 *   year=1976
 * }
 * {@literal @}INPROCEEDINGS{Mar79,
 *   author="Martin, G.N.N.",
 *   title="Range encoding: an algorithm for removing redundancy from a digitised
 *    message",
 *   booktitle="Video &amp; Data Recording Conference",
 *   year=1979,
 *   address="Southampton",
 *   month=Jul
 * }
 * {@literal @}ARTICLE{MNW98,
 *   author="Alistair Moffat and Radford Neal and Ian H. Witten",
 *   title="Arithmetic Coding Revisited",
 *   journal="{ACM} Transactions on Information Systems",
 *   year=1998,
 *   volume=16,
 *   number=3,
 *   pages="256--294",
 *   month=Jul,
 *   URL="http://www.stanford.edu/class/ee398/handouts/papers/Moffat98ArithmCoding.pdf"
 * }
 * </pre>
 */
final class RangeDecoder {
    // The resolution of fractional-precision bit usage measurements, i.e.,
    // 3 => 1/8th bits.
    static final int BITRES = 3;

    // Constants used by the entropy encoder/decoder.

    // The number of bits to output at a time.
    private static final int EC_SYM_BITS = 8;
    // The total number of bits in each of the state registers.
    private static final int EC_CODE_BITS = 32;
    // The maximum symbol value.
    private static final int EC_SYM_MAX = (1 << EC_SYM_BITS) - 1;
    // Carry bit of the high-order range symbol.
    private static final int EC_CODE_TOP = 1 << (EC_CODE_BITS - 1);
    // Low-order bit of the high-order range symbol.
    private static final int EC_CODE_BOT = EC_CODE_TOP >>> EC_SYM_BITS;
    // The number of bits available for the last, partial symbol in the code field.
    private static final int EC_CODE_EXTRA = ((EC_CODE_BITS - 2) % EC_SYM_BITS) + 1;
    // OPT: ec_window must be at least 32 bits, but if you have fast arithmetic on a
    // larger type, you can speed up the decoder by using it here.
    private static final int EC_WINDOW_SIZE = Integer.SIZE;
    // The number of bits to use for the range-coded part of unsigned integers.
    private static final int EC_UINT_BITS = 8;

    // The minimum probability of an energy delta (out of 32768).
    private static final int LAPLACE_LOG_MINP = 0;
    private static final int LAPLACE_MINP = 1 << LAPLACE_LOG_MINP;
    // The minimum number of guaranteed representable energy deltas (in one
    // direction).
    private static final int LAPLACE_NMIN = 16;
    // The size of the buffer.
    final int storage;
    // Buffered input.
    private final byte[] buf;
    private final int bufOff;
    // The total number of whole bits read.
    // This does not include partial bits currently in the range coder.
    int nbits_total;
    // The offset at which the last byte containing raw bits was read.
    private int end_offs;
    // Bits that will be read from the end.
    private int end_window;
    // Number of valid bits in end_window.
    private int nend_bits;
    // The offset at which the next range coder byte will be read.
    private int offs;
    // The number of values in the current range.
    private int rng;
    // The difference between the top of the current range and the input value,
    // minus one.
    private int val;
    // The saved normalization factor from ec_decode().
    private int ext;
    // The last input byte read, part of which is still to be used.
    private int rem;

    RangeDecoder(byte[] buf, int bufOff, int storage) {
        this.buf = buf;
        this.bufOff = bufOff;
        this.storage = storage;
        this.end_offs = 0;
        this.end_window = 0;
        this.nend_bits = 0;
        // This is the offset from which ec_tell() will subtract partial bits.
        // The final value after the ec_dec_normalize() call will be the same as in
        // the encoder, but we have to compensate for the bits that are added there.
        this.nbits_total = (EC_CODE_BITS + 1) - (((EC_CODE_BITS - EC_CODE_EXTRA) / EC_SYM_BITS) * EC_SYM_BITS);
        this.offs = 0;
        this.rng = 1 << EC_CODE_EXTRA;
        this.rem = ec_read_byte();
        this.val = rng - 1 - (rem >>> (EC_SYM_BITS - EC_CODE_EXTRA));
        // Normalize the interval.
        ec_dec_normalize();
    }

    private int ec_read_byte() {
        return (offs < storage) ? Byte.toUnsignedInt(buf[bufOff + offs++]) : 0;
    }

    /**
     * Normalizes the contents of {@code val} and {@code rng} so that {@code rng} lies entirely in the
     * high-order symbol.
     */
    private void ec_dec_normalize() {
        // If the range is too small, rescale it and input some bits.
        while (Integer.compareUnsigned(rng, EC_CODE_BOT) <= 0) {
            this.nbits_total += EC_SYM_BITS;
            this.rng <<= EC_SYM_BITS;
            // Use up the remaining bits from our last symbol.
            int sym = rem;
            // Read the next value from the input.
            this.rem = ec_read_byte();
            // Take the rest of the bits we need from this new symbol.
            sym = ((sym << EC_SYM_BITS) | rem) >>> (EC_SYM_BITS - EC_CODE_EXTRA);
            // And subtract them from val, capped to be less than EC_CODE_TOP.
            this.val = ((val << EC_SYM_BITS) + (EC_SYM_MAX & ~sym)) & (EC_CODE_TOP - 1);
        }
    }

    /**
     * The probability of having a "one" is {@code 1/(1<<_logp)}.
     */
    int ec_dec_bit_logp(int _logp) {
        int r = rng;
        int d = val;
        int s = r >>> _logp;
        boolean ret = Integer.compareUnsigned(d, s) < 0;
        if (!ret) {
            this.val = d - s;
        }
        this.rng = ret ? s : (r - s);
        ec_dec_normalize();
        return ret ? 1 : 0;
    }

    int ec_dec_icdf(byte[] _icdf, int _ftb) {
        int s = rng;
        int d = val;
        int r = s >>> _ftb;
        int ret = -1;
        int t;
        do {
            t = s;
            s = r * _icdf[++ret];
        } while (d < s);
        this.val = d - s;
        this.rng = t - s;
        ec_dec_normalize();
        return ret;
    }

    int ec_dec_uint(int _ft) {
        // In order to optimize EC_ILOG(), it is undefined for the value 0.
        celt_assert(Integer.compareUnsigned(_ft, 1) > 0);
        _ft--;
        int ftb = EC_ILOG(_ft);
        if (ftb > EC_UINT_BITS) {
            ftb -= EC_UINT_BITS;
            int ft = (_ft >>> ftb) + 1;
            int s = ec_decode(ft);
            ec_dec_update(s, s + 1, ft);
            int t = (s << ftb) | ec_dec_bits(ftb);
            if (Integer.compareUnsigned(t, _ft) <= 0) {
                return t;
            }
            // A corrupt stream: saturate.
            return _ft;
        } else {
            _ft++;
            int s = ec_decode(_ft);
            ec_dec_update(s, s + 1, _ft);
            return s;
        }
    }

    int ec_decode(int _ft) {
        this.ext = Integer.divideUnsigned(rng, _ft);
        int s = Integer.divideUnsigned(val, ext);
        return _ft - Math.min(s + 1, _ft);
    }

    void ec_dec_update(int _fl, int _fh, int _ft) {
        int s = ext * (_ft - _fh);
        this.val -= s;
        this.rng = (_fl != 0) ? (ext * (_fh - _fl)) : (rng - s);
        ec_dec_normalize();
    }

    int ec_dec_bits(int _bits) {
        int window = end_window;
        int available = nend_bits;
        if (available < _bits) {
            while (available <= EC_WINDOW_SIZE - EC_SYM_BITS) {
                window |= ec_read_byte_from_end() << available;
                available += EC_SYM_BITS;
            }
        }
        int ret = window & ((1 << _bits) - 1);
        window >>>= _bits;
        available -= _bits;
        this.end_window = window;
        this.nend_bits = available;
        this.nbits_total += _bits;
        return ret;
    }

    private int ec_read_byte_from_end() {
        return (end_offs < storage) ? Byte.toUnsignedInt(buf[bufOff + storage - ++end_offs]) : 0;
    }

    int ec_laplace_decode(int fs, int decay) {
        int val = 0;
        int fm = ec_decode_bin(15);
        int fl = 0;
        if (fm >= fs) {
            val++;
            fl = fs;
            int ft = 32768 - (LAPLACE_MINP * 2 * LAPLACE_NMIN) - fs;
            fs = ((ft * (16384 - decay)) >> 15) + LAPLACE_MINP;
            // Search the decaying part of the PDF.
            while ((fs > LAPLACE_MINP) && (fm >= (fl + (2 * fs)))) {
                fs *= 2;
                fl += fs;
                fs = ((fs - (2 * LAPLACE_MINP)) * decay) >> 15;
                fs += LAPLACE_MINP;
                val++;
            }
            // Everything beyond that has probability LAPLACE_MINP.
            if (fs <= LAPLACE_MINP) {
                int di = (fm - fl) >> (LAPLACE_LOG_MINP + 1);
                val += di;
                fl += 2 * di * LAPLACE_MINP;
            }
            if (fm < (fl + fs)) {
                val = -val;
            } else {
                fl += fs;
            }
        }
        celt_assert(fl < 32768);
        celt_assert(fs > 0);
        celt_assert(fl <= fm);
        celt_assert(fm < Math.min(fl + fs, 32768));
        ec_dec_update(fl, Math.min(fl + fs, 32768), 32768);
        return val;
    }

    int ec_decode_bin(int _bits) {
        this.ext = rng >>> _bits;
        int s = Integer.divideUnsigned(val, ext);
        return (1 << _bits) - Math.min(s + 1, 1 << _bits);
    }

    /**
     * Returns the number of bits "used" by the encoded or decoded symbols so far.
     * This same number can be computed in either the encoder or the decoder, and is
     * suitable for making coding decisions.
     *
     * @return The number of bits.
     * This will always be slightly larger than the exact value (e.g., all
     * rounding error is in the positive direction).
     */
    int ec_tell() {
        return nbits_total - EC_ILOG(rng);
    }

    /**
     * Returns the number of bits "used" by the encoded or decoded symbols so far.
     * This same number can be computed in either the encoder or the decoder, and is
     * suitable for making coding decisions.
     *
     * @return The number of bits scaled by {@code 2**BITRES}.
     * This will always be slightly larger than the exact value (e.g., all
     * rounding error is in the positive direction).
     */
    int ec_tell_frac() {
        // To handle the non-integral number of bits still left in the encoder/decoder
        // state, we compute the worst-case number of bits of val that must be
        // encoded to ensure that the value is inside the range for any possible
        // subsequent bits.
        // The computation here is independent of val itself (the decoder does not
        // even track that value), even though the real number of bits used after
        // ec_enc_done() may be 1 smaller if rng is a power of two and the
        // corresponding trailing bits of val are all zeros.
        // If we did try to track that special case, then coding a value with a
        // probability of 1/(1<<n) might sometimes appear to use more than n bits.
        // This may help explain the surprising result that a newly initialized
        // encoder or decoder claims to have used 1 bit.
        int nbits = nbits_total << BITRES;
        int l = EC_ILOG(rng);
        int r = rng >>> (l - 16);
        for (int i = 0; i < BITRES; i++) {
            r = (r * r) >>> 15;
            int b = r >>> 16;
            l = (l << 1) | b;
            r >>>= b;
        }
        return nbits - l;
    }

    int rng() {
        return rng;
    }
}
