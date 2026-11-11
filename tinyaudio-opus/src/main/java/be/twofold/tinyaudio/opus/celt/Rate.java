package be.twofold.tinyaudio.opus.celt;

import static be.twofold.tinyaudio.opus.celt.RangeDecoder.BITRES;
import static be.twofold.tinyaudio.opus.celt.Utils.celt_assert;

final class Rate {
    private static final int LOG_MAX_PSEUDO = 6;
    private static final int MAX_FINE_BITS = 8;
    private static final int FINE_OFFSET = 21;

    private static final int ALLOC_STEPS = 6;

    private static final byte[] LOG2_FRAC_TABLE = {
        0,
        8, 13,
        16, 19, 21, 23,
        24, 26, 27, 28, 29, 30, 31, 32,
        32, 33, 34, 34, 35, 36, 36, 37, 37
    };

    private Rate() {
    }

    static int compute_allocation(
        CeltMode m, int end, int[] offsets, int[] cap, int alloc_trim, int[] intensity, int[] dual_stereo,
        int total, int[] balance, int[] pulses, int[] ebits, int[] fine_priority, int C, int LM, RangeDecoder ec
    ) {
        total = Math.max(total, 0);
        int len = m.nbEBands;
        int skip_start = 0;
        /* Reserve a bit to signal the end of manually skipped bands. */
        int skip_rsv = total >= 1 << BITRES ? 1 << BITRES : 0;
        total -= skip_rsv;
        /* Reserve bits for the intensity and dual stereo parameters. */
        int intensity_rsv = 0;
        int dual_stereo_rsv = 0;
        if (C == 2) {
            intensity_rsv = LOG2_FRAC_TABLE[end];
            if (intensity_rsv > total) {
                intensity_rsv = 0;
            } else {
                total -= intensity_rsv;
                dual_stereo_rsv = total >= 1 << BITRES ? 1 << BITRES : 0;
                total -= dual_stereo_rsv;
            }
        }
        int[] bits1 = new int[len];
        int[] bits2 = new int[len];
        int[] thresh = new int[len];
        int[] trim_offset = new int[len];

        for (int j = 0; j < end; j++) {
            /* Below this threshold, we're sure not to allocate any PVQ bits */
            thresh[j] = Math.max((C) << BITRES, (3 * (m.eBands[j + 1] - m.eBands[j]) << LM << BITRES) >> 4);
            /* Tilt of the allocation curve */
            trim_offset[j] = C * (m.eBands[j + 1] - m.eBands[j]) * (alloc_trim - 5 - LM) * (end - j - 1) * (1 << (LM + BITRES)) >> 6;
            /* Giving less resolution to single-coefficient bands because they get
               more benefit from having one coarse value per coefficient*/
            if ((m.eBands[j + 1] - m.eBands[j]) << LM == 1) {
                trim_offset[j] -= C << BITRES;
            }
        }
        int lo = 1;
        int hi = m.nbAllocVectors - 1;
        do {
            boolean done = false;
            int psum = 0;
            int mid = (lo + hi) >> 1;
            for (int j = end; j-- > 0; ) {
                int N = m.eBands[j + 1] - m.eBands[j];
                int bitsj = C * N * m.allocVectors[mid * len + j] << LM >> 2;
                if (bitsj > 0) {
                    bitsj = Math.max(0, bitsj + trim_offset[j]);
                }
                bitsj += offsets[j];
                if (bitsj >= thresh[j] || done) {
                    done = true;
                    /* Don't allocate more than we can actually use */
                    psum += Math.min(bitsj, cap[j]);
                } else {
                    if (bitsj >= C << BITRES) {
                        psum += C << BITRES;
                    }
                }
            }
            if (psum > total) {
                hi = mid - 1;
            } else {
                lo = mid + 1;
            }
        } while (lo <= hi);
        hi = lo--;
        for (int j = 0; j < end; j++) {
            int N = m.eBands[j + 1] - m.eBands[j];
            int bits1j = C * N * m.allocVectors[lo * len + j] << LM >> 2;
            int bits2j = hi >= m.nbAllocVectors ? cap[j] : C * N * m.allocVectors[hi * len + j] << LM >> 2;
            if (bits1j > 0) {
                bits1j = Math.max(0, bits1j + trim_offset[j]);
            }
            if (bits2j > 0) {
                bits2j = Math.max(0, bits2j + trim_offset[j]);
            }
            if (lo > 0) {
                bits1j += offsets[j];
            }
            bits2j += offsets[j];
            if (offsets[j] > 0) {
                skip_start = j;
            }
            bits2j = Math.max(0, bits2j - bits1j);
            bits1[j] = bits1j;
            bits2[j] = bits2j;
        }
        return interp_bits2pulses(m, end, skip_start, bits1, bits2, thresh, cap,
            total, balance, skip_rsv, intensity, intensity_rsv, dual_stereo, dual_stereo_rsv,
            pulses, ebits, fine_priority, C, LM, ec);
    }

    private static int interp_bits2pulses(
        CeltMode m, int end, int skip_start,
        int[] bits1, int[] bits2, int[] thresh, int[] cap, int total, int[] _balance,
        int skip_rsv, int[] intensity, int intensity_rsv, int[] dual_stereo, int dual_stereo_rsv, int[] bits,
        int[] ebits, int[] fine_priority, int C, int LM, RangeDecoder ec
    ) {
        int alloc_floor = C << BITRES;
        int stereo = C > 1 ? 1 : 0;

        int lo = 0;
        int hi = 1 << ALLOC_STEPS;
        for (int i = 0; i < ALLOC_STEPS; i++) {
            int mid = (lo + hi) >> 1;
            int psum = 0;
            boolean done = false;
            for (int j = end; j-- > 0; ) {
                int tmp = bits1[j] + (mid * bits2[j] >> ALLOC_STEPS);
                if (tmp >= thresh[j] || done) {
                    done = true;
                    /* Don't allocate more than we can actually use */
                    psum += Math.min(tmp, cap[j]);
                } else {
                    if (tmp >= alloc_floor) {
                        psum += alloc_floor;
                    }
                }
            }
            if (psum > total) {
                hi = mid;
            } else {
                lo = mid;
            }
        }
        int psum = 0;
        boolean done = false;
        for (int j = end; j-- > 0; ) {
            int tmp = bits1[j] + (lo * bits2[j] >> ALLOC_STEPS);
            if (tmp < thresh[j] && !done) {
                if (tmp >= alloc_floor) {
                    tmp = alloc_floor;
                } else {
                    tmp = 0;
                }
            } else {
                done = true;
            }
            /* Don't allocate more than we can actually use */
            tmp = Math.min(tmp, cap[j]);
            bits[j] = tmp;
            psum += tmp;
        }

        /* Decide which bands to skip, working backwards from the end. */
        int percoeff;
        int left;
        int codedBands;
        for (codedBands = end; ; codedBands--) {
            int j = codedBands - 1;
            /* Never skip the first band, nor a band that has been boosted by
                dynalloc.
               In the first case, we'd be coding a bit to signal we're going to waste
                all the other bits.
               In the second case, we'd be coding a bit to redistribute all the bits
                we just signaled should be cocentrated in this band. */
            if (j <= skip_start) {
                /* Give the bit we reserved to end skipping back. */
                total += skip_rsv;
                break;
            }
            /*Figure out how many left-over bits we would be adding to this band.
              This can include bits we've stolen back from higher, skipped bands.*/
            left = total - psum;
            percoeff = left / m.eBands[codedBands];
            left -= m.eBands[codedBands] * percoeff;
            int rem = Math.max(left - m.eBands[j], 0);
            int band_width = m.eBands[codedBands] - m.eBands[j];
            int band_bits = bits[j] + percoeff * band_width + rem;
            /*Only code a skip decision if we're above the threshold for this band.
              Otherwise it is force-skipped.
              This ensures that we have enough bits to code the skip flag.*/
            if (band_bits >= Math.max(thresh[j], alloc_floor + (1 << BITRES))) {
                /*The encoder signals whether it skips this band.*/
                if (ec.ec_dec_bit_logp(1) != 0) {
                    break;
                }
                /*We used a bit to skip this band.*/
                psum += 1 << BITRES;
                band_bits -= 1 << BITRES;
            }
            /*Reclaim the bits originally allocated to this band.*/
            psum -= bits[j] + intensity_rsv;
            if (intensity_rsv > 0) {
                intensity_rsv = LOG2_FRAC_TABLE[j];
            }
            psum += intensity_rsv;
            if (band_bits >= alloc_floor) {
                /*If we have enough for a fine energy bit per channel, use it.*/
                psum += alloc_floor;
                bits[j] = alloc_floor;
            } else {
                /*Otherwise this band gets nothing at all.*/
                bits[j] = 0;
            }
        }

        celt_assert(codedBands > 0);
        /* Decode the intensity and dual stereo parameters. */
        if (intensity_rsv > 0) {
            intensity[0] = ec.ec_dec_uint(codedBands + 1);
        } else {
            intensity[0] = 0;
        }
        if (intensity[0] <= 0) {
            total += dual_stereo_rsv;
            dual_stereo_rsv = 0;
        }
        if (dual_stereo_rsv > 0) {
            dual_stereo[0] = ec.ec_dec_bit_logp(1);
        } else {
            dual_stereo[0] = 0;
        }

        /* Allocate the remaining bits */
        left = total - psum;
        percoeff = left / m.eBands[codedBands];
        left -= m.eBands[codedBands] * percoeff;
        for (int j = 0; j < codedBands; j++) {
            bits[j] += (percoeff * (m.eBands[j + 1] - m.eBands[j]));
        }
        for (int j = 0; j < codedBands; j++) {
            int tmp = Math.min(left, m.eBands[j + 1] - m.eBands[j]);
            bits[j] += tmp;
            left -= tmp;
        }

        int balance = 0;
        int logM = LM << BITRES;
        for (int j = 0; j < codedBands; j++) {

            celt_assert(bits[j] >= 0);
            int N0 = m.eBands[j + 1] - m.eBands[j];
            bits[j] += balance;

            int N = N0 << LM;
            int excess;
            if (N > 1) {
                excess = Math.max(bits[j] - cap[j], 0);
                bits[j] -= excess;

                /* Compensate for the extra DoF in stereo */
                int den = (C * N + ((C == 2 && N > 2 && dual_stereo[0] == 0 && j < intensity[0]) ? 1 : 0));

                int NClogN = den * (m.logN[j] + logM);

                /* Offset for the number of fine bits by log2(N)/2 + FINE_OFFSET
                   compared to their "fair share" of total/N */
                int offset = (NClogN >> 1) - den * FINE_OFFSET;

                /* N=2 is the only point that doesn't match the curve */
                if (N == 2) {
                    offset += den << BITRES >> 2;
                }

                /* Changing the offset for allocating the second and third
                    fine energy bit */
                if (bits[j] + offset < den * 2 << BITRES) {
                    offset += NClogN >> 2;
                } else if (bits[j] + offset < den * 3 << BITRES) {
                    offset += NClogN >> 3;
                }

                /* Divide with rounding */
                ebits[j] = Math.max(0, (bits[j] + offset + (den << (BITRES - 1))) / (den << BITRES));

                /* Make sure not to bust */
                if (C * ebits[j] > (bits[j] >> BITRES)) {
                    ebits[j] = bits[j] >> stereo >> BITRES;
                }

                /* More than that is useless because that's about as far as PVQ can go */
                ebits[j] = Math.min(ebits[j], MAX_FINE_BITS);

                /* If we rounded down or capped this band, make it a candidate for the
                    final fine energy pass */
                fine_priority[j] = ebits[j] * (den << BITRES) >= bits[j] + offset ? 1 : 0;

                /* Remove the allocated fine bits; the rest are assigned to PVQ */
                bits[j] -= C * ebits[j] << BITRES;
            } else {
                /* For N=1, all bits go to fine energy except for a single sign bit */
                excess = Math.max(0, bits[j] - (C << BITRES));
                bits[j] -= excess;
                ebits[j] = 0;
                fine_priority[j] = 1;
            }

            /* Fine energy can't take advantage of the re-balancing in
                quant_all_bands().
               Instead, do the re-balancing here.*/
            if (excess > 0) {
                int extra_fine = Math.min(excess >> (stereo + BITRES), MAX_FINE_BITS - ebits[j]);
                ebits[j] += extra_fine;
                int extra_bits = extra_fine * C << BITRES;
                fine_priority[j] = (extra_bits >= excess - balance) ? 1 : 0;
                excess -= extra_bits;
            }
            balance = excess;

            celt_assert(bits[j] >= 0);
            celt_assert(ebits[j] >= 0);
        }
        /* Save any remaining bits over the cap for the rebalancing in
            quant_all_bands(). */
        _balance[0] = balance;

        /* The skipped bands use all their bits for fine energy. */
        for (int j = codedBands; j < end; j++) {
            ebits[j] = bits[j] >> stereo >> BITRES;
            celt_assert(C * ebits[j] << BITRES == bits[j]);
            bits[j] = 0;
            fine_priority[j] = ebits[j] < 1 ? 1 : 0;
        }
        return codedBands;
    }

    static int get_pulses(int i) {
        return i < 8 ? i : (8 + (i & 7)) << ((i >> 3) - 1);
    }

    static int bits2pulses(CeltMode m, int band, int LM, int bits) {
        int cacheOff = m.cacheIndex[(LM + 1) * m.nbEBands + band];
        bits--;

        int lo = 0;
        int hi = m.cacheBits[cacheOff];
        for (int i = 0; i < LOG_MAX_PSEUDO; i++) {
            int mid = (lo + hi + 1) >> 1;
            /* OPT: Make sure this is implemented with a conditional move */
            if (m.cacheBits[cacheOff + mid] >= bits) {
                hi = mid;
            } else {
                lo = mid;
            }
        }
        if (bits - (lo == 0 ? -1 : m.cacheBits[cacheOff + lo]) <= m.cacheBits[cacheOff + hi] - bits) {
            return lo;
        } else {
            return hi;
        }
    }

    static int pulses2bits(CeltMode m, int band, int LM, int pulses) {
        int cacheOff = m.cacheIndex[(LM + 1) * m.nbEBands + band];
        return pulses == 0 ? 0 : m.cacheBits[cacheOff + pulses] + 1;
    }
}
