package be.twofold.tinyaudio.opus.celt;

import static be.twofold.tinyaudio.opus.celt.Utils.EC_ILOG;
import static be.twofold.tinyaudio.opus.celt.Utils.celt_assert;

final class RangeDecoder {
    private static final int EC_SYM_BITS = 8;
    private static final int EC_CODE_BITS = 32;
    private static final int EC_SYM_MAX = (1 << EC_SYM_BITS) - 1;
    private static final int EC_CODE_TOP = 1 << (EC_CODE_BITS - 1);
    private static final int EC_CODE_BOT = EC_CODE_TOP >>> EC_SYM_BITS;
    private static final int EC_CODE_EXTRA = ((EC_CODE_BITS - 2) % EC_SYM_BITS) + 1;

    private static final int EC_WINDOW_SIZE = Integer.SIZE;
    private static final int EC_UINT_BITS = 8;
    private static final int BITRES = 3;

    private static final int LAPLACE_LOG_MINP = 0;
    private static final int LAPLACE_MINP = 1 << LAPLACE_LOG_MINP;
    private static final int LAPLACE_NMIN = 16;

    private final byte[] buf;
    private final int storage;
    private int end_offs;
    private int end_window;
    private int nend_bits;
    private int nbits_total;
    private int offs;
    private int rng;
    private int val;
    private int ext;
    private int rem;

    RangeDecoder(byte[] buf, int storage) {
        this.buf = buf;
        this.storage = storage;
        this.end_offs = 0;
        this.end_window = 0;
        this.nend_bits = 0;
        this.nbits_total = (EC_CODE_BITS + 1) - (((EC_CODE_BITS - EC_CODE_EXTRA) / EC_SYM_BITS) * EC_SYM_BITS);
        this.offs = 0;
        this.rng = 1 << EC_CODE_EXTRA;
        this.rem = ec_read_byte();
        this.val = rng - 1 - (rem >>> (EC_SYM_BITS - EC_CODE_EXTRA));
        ec_dec_normalize();
    }

    int rng() {
        return rng;
    }

    int ec_decode(int _ft) {
        this.ext = Integer.divideUnsigned(rng, _ft);
        int s = Integer.divideUnsigned(val, ext);
        return _ft - Math.min(s + 1, _ft);
    }

    int ec_decode_bin(int _bits) {
        this.ext = rng >>> _bits;
        int s = Integer.divideUnsigned(val, ext);
        return (1 << _bits) - Math.min(s + 1, 1 << _bits);
    }

    void ec_dec_update(int _fl, int _fh, int _ft) {
        int s = ext * (_ft - _fh);
        this.val -= s;
        this.rng = (_fl != 0) ? (ext * (_fh - _fl)) : (rng - s);
        ec_dec_normalize();
    }

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
        celt_assert(Integer.compareUnsigned(_ft, 1) > 0);
        _ft--;
        int ftb = EC_ILOG(_ft);
        if (ftb > EC_UINT_BITS) {
            ftb -= EC_UINT_BITS;
            int ft = (_ft >>> ftb) + 1;
            int s = ec_decode(ft);
            ec_dec_update(s, s + 1, ft);
            int t = (s << ftb) | ec_dec_bits(ftb);
            return Integer.compareUnsigned(t, _ft) <= 0 ? t : _ft;
        } else {
            _ft++;
            int s = ec_decode(_ft);
            ec_dec_update(s, s + 1, _ft);
            return s;
        }
    }

    int ec_dec_bits(int _bits) {
        int window = end_window;
        int available = nend_bits;
        if (available < _bits) {
            do {
                window |= ec_read_byte_from_end() << available;
                available += EC_SYM_BITS;
            } while (available <= (EC_WINDOW_SIZE - EC_SYM_BITS));
        }
        int ret = window & ((1 << _bits) - 1);
        window >>>= _bits;
        available -= _bits;
        this.end_window = window;
        this.nend_bits = available;
        this.nbits_total += _bits;
        return ret;
    }

    int ec_tell() {
        return nbits_total - EC_ILOG(rng);
    }

    int ec_tell_frac() {
        int nbits = nbits_total << BITRES;
        int l = EC_ILOG(rng);
        int r = rng >>> (l - 16);
        for (int i = BITRES; i-- > 0; ) {
            r = (r * r) >>> 15;
            int b = r >>> 16;
            l = (l << 1) | b;
            r >>>= b;
        }
        return nbits - l;
    }

    //
    // Helpers
    //

    private void ec_dec_normalize() {
        while (Integer.compareUnsigned(rng, EC_CODE_BOT) <= 0) {
            this.nbits_total += EC_SYM_BITS;
            this.rng <<= EC_SYM_BITS;
            int sym = rem;
            this.rem = ec_read_byte();
            sym = ((sym << EC_SYM_BITS) | rem) >>> (EC_SYM_BITS - EC_CODE_EXTRA);
            this.val = ((val << EC_SYM_BITS) + (EC_SYM_MAX & ~sym)) & (EC_CODE_TOP - 1);
        }
    }

    private int ec_read_byte() {
        return (offs < storage) ? Byte.toUnsignedInt(buf[offs++]) : 0;
    }

    private int ec_read_byte_from_end() {
        return (end_offs < storage) ? Byte.toUnsignedInt(buf[storage - ++end_offs]) : 0;
    }

    //
    // Laplace
    //

    int ec_laplace_decode(int fs, int decay) {
        int val = 0;
        int fm = ec_decode_bin(15);
        int fl = 0;
        if (fm >= fs) {
            val++;
            fl = fs;
            int ft = 32768 - (LAPLACE_MINP * 2 * LAPLACE_NMIN) - fs;
            fs = ((ft * (16384 - decay)) >> 15) + LAPLACE_MINP;
            while ((fs > LAPLACE_MINP) && (fm >= (fl + (2 * fs)))) {
                fs *= 2;
                fl += fs;
                fs = ((fs - (2 * LAPLACE_MINP)) * decay) >> 15;
                fs += LAPLACE_MINP;
                val++;
            }
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
}
