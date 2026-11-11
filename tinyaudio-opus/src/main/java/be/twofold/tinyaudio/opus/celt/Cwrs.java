package be.twofold.tinyaudio.opus.celt;

import static be.twofold.tinyaudio.opus.celt.Utils.celt_assert;

/**
 * Although derived separately, the pulse vector coding scheme is equivalent to
 * a Pyramid Vector Quantizer \cite{Fis86}.
 * Some additional notes about an early version appear at
 * <a href="http://people.xiph.org/~tterribe/notes/cwrs.html">http://people.xiph.org/~tterribe/notes/cwrs.html</a>,
 * but the codebook ordering and the definitions of some terms have evolved since that was written.
 *
 * <p>The conversion from a pulse vector to an integer index (encoding) and back
 * (decoding) is governed by two related functions, {@code V(N,K)} and {@code U(N,K)}.
 *
 * <p>{@code V(N,K)} = the number of combinations, with replacement, of N items, taken K
 * at a time, when a sign bit is added to each item taken at least once (i.e.,
 * the number of N-dimensional unit pulse vectors with K pulses).
 * One way to compute this is via
 * <pre>{@code
 *   V(N,K) = K>0 ? sum(k=1...K,2**k*choose(N,k)*choose(K-1,k-1)) : 1,
 * }</pre>
 * where {@code choose()} is the binomial function.
 * A table of values for {@code N<10} and {@code K<10} looks like:
 * <pre>{@code
 * V[10][10] = {
 *   {1,  0,   0,    0,    0,     0,     0,      0,      0,       0},
 *   {1,  2,   2,    2,    2,     2,     2,      2,      2,       2},
 *   {1,  4,   8,   12,   16,    20,    24,     28,     32,      36},
 *   {1,  6,  18,   38,   66,   102,   146,    198,    258,     326},
 *   {1,  8,  32,   88,  192,   360,   608,    952,   1408,    1992},
 *   {1, 10,  50,  170,  450,  1002,  1970,   3530,   5890,    9290},
 *   {1, 12,  72,  292,  912,  2364,  5336,  10836,  20256,   35436},
 *   {1, 14,  98,  462, 1666,  4942, 12642,  28814,  59906,  115598},
 *   {1, 16, 128,  688, 2816,  9424, 27008,  68464, 157184,  332688},
 *   {1, 18, 162,  978, 4482, 16722, 53154, 148626, 374274,  864146}
 * };
 * }</pre>
 *
 * <p>{@code U(N,K)} = the number of such combinations wherein N-1 objects are taken at
 * most K-1 at a time.
 * This is given by
 * <pre>{@code
 *   U(N,K) = sum(k=0...K-1,V(N-1,k))
 *          = K>0 ? (V(N-1,K-1) + V(N,K-1))/2 : 0.
 * }</pre>
 * The latter expression also makes clear that {@code U(N,K)} is half the number of such
 * combinations wherein the first object is taken at least once.
 * Although it may not be clear from either of these definitions, {@code U(N,K)} is the
 * natural function to work with when enumerating the pulse vector codebooks,
 * not {@code V(N,K)}.
 * {@code U(N,K)} is not well-defined for {@code N=0}, but with the extension
 * <pre>{@code
 *   U(0,K) = K>0 ? 0 : 1,
 * }</pre>
 * the function becomes symmetric: {@code U(N,K) = U(K,N)}, with a similar table:
 * <pre>{@code
 * U[10][10] = {
 *   {1, 0,  0,   0,    0,    0,     0,     0,      0,      0},
 *   {0, 1,  1,   1,    1,    1,     1,     1,      1,      1},
 *   {0, 1,  3,   5,    7,    9,    11,    13,     15,     17},
 *   {0, 1,  5,  13,   25,   41,    61,    85,    113,    145},
 *   {0, 1,  7,  25,   63,  129,   231,   377,    575,    833},
 *   {0, 1,  9,  41,  129,  321,   681,  1289,   2241,   3649},
 *   {0, 1, 11,  61,  231,  681,  1683,  3653,   7183,  13073},
 *   {0, 1, 13,  85,  377, 1289,  3653,  8989,  19825,  40081},
 *   {0, 1, 15, 113,  575, 2241,  7183, 19825,  48639, 108545},
 *   {0, 1, 17, 145,  833, 3649, 13073, 40081, 108545, 265729}
 * };
 * }</pre>
 *
 * <p>With this extension, {@code V(N,K)} may be written in terms of {@code U(N,K)}:
 * <pre>{@code
 *   V(N,K) = U(N,K) + U(N,K+1)
 * }</pre>
 * for all {@code N>=0}, {@code K>=0}.
 * Thus {@code U(N,K+1)} represents the number of combinations where the first element
 * is positive or zero, and {@code U(N,K)} represents the number of combinations where
 * it is negative.
 * With a large enough table of {@code U(N,K)} values, we could write {@code O(N)} encoding
 * and {@code O(min(N*log(K),N+K))} decoding routines, but such a table would be
 * prohibitively large for small embedded devices (K may be as large as 32767
 * for small N, and N may be as large as 200).
 *
 * <p>Both functions obey the same recurrence relation:
 * <pre>{@code
 *   V(N,K) = V(N-1,K) + V(N,K-1) + V(N-1,K-1),
 *   U(N,K) = U(N-1,K) + U(N,K-1) + U(N-1,K-1),
 * }</pre>
 * for all {@code N>0}, {@code K>0}, with different initial conditions at {@code N=0} or {@code K=0}.
 * This allows us to construct a row of one of the tables above given the
 * previous row or the next row.
 * Thus we can derive {@code O(NK)} encoding and decoding routines with {@code O(K)} memory
 * using only addition and subtraction.
 *
 * <p>When encoding, we build up from the {@code U(2,K)} row and work our way forwards.
 * When decoding, we need to start at the {@code U(N,K)} row and work our way backwards,
 * which requires a means of computing {@code U(N,K)}.
 * {@code U(N,K)} may be computed from two previous values with the same N:
 * <pre>{@code
 *   U(N,K) = ((2*N-1)*U(N,K-1) - U(N,K-2))/(K-1) + U(N,K-2)
 * }</pre>
 * for all {@code N>1}, and since {@code U(N,K)} is symmetric, a similar relation holds for two
 * previous values with the same K:
 * <pre>{@code
 *   U(N,K>1) = ((2*K-1)*U(N-1,K) - U(N-2,K))/(N-1) + U(N-2,K)
 * }</pre>
 * for all {@code K>1}.
 * This allows us to construct an arbitrary row of the {@code U(N,K)} table by starting
 * with the first two values, which are constants.
 * This saves roughly 2/3 the work in our {@code O(NK)} decoding routine, but costs {@code O(K)}
 * multiplications.
 * Similar relations can be derived for {@code V(N,K)}, but are not used here.
 *
 * <p>For {@code N>0} and {@code K>0}, {@code U(N,K)} and {@code V(N,K)} take on the form of an
 * (N-1)-degree polynomial for fixed N.
 * The first few are
 * <pre>{@code
 *   U(1,K) = 1,
 *   U(2,K) = 2*K-1,
 *   U(3,K) = (2*K-2)*K+1,
 *   U(4,K) = (((4*K-6)*K+8)*K-3)/3,
 *   U(5,K) = ((((2*K-4)*K+10)*K-8)*K+3)/3,
 * }</pre>
 * and
 * <pre>{@code
 *   V(1,K) = 2,
 *   V(2,K) = 4*K,
 *   V(3,K) = 4*K*K+2,
 *   V(4,K) = 8*(K*K+2)*K/3,
 *   V(5,K) = ((4*K*K+20)*K*K+6)/3,
 * }</pre>
 * for all {@code K>0}.
 * This allows us to derive {@code O(N)} encoding and {@code O(N*log(K))} decoding routines for
 * small N (and indeed decoding is also {@code O(N)} for {@code N<3}).
 *
 * <pre>
 * {@literal @}ARTICLE{Fis86,
 *   author="Thomas R. Fischer",
 *   title="A Pyramid Vector Quantizer",
 *   journal="IEEE Transactions on Information Theory",
 *   volume="IT-32",
 *   number=4,
 *   pages="568--583",
 *   month=Jul,
 *   year=1986
 * }
 * </pre>
 */
final class Cwrs {
    private Cwrs() {
    }

    /**
     * Returns the energy of the decoded vector
     */
    static float decode_pulses(int[] _y, int _n, int _k, RangeDecoder _dec) {
        celt_assert(_k > 0);
        int[] u = new int[_k + 2];
        return cwrsi(_n, _k, _dec.ec_dec_uint(ncwrs_urow(_n, _k, u)), _y, u);
    }

    /**
     * Returns the {@code _i}'th combination of {@code _k} elements chosen from a set of size {@code _n}
     * with associated sign bits.
     * {@code _y}: Returns the vector of pulses.
     * {@code _u}: Must contain entries {@code [0..._k+1]} of row {@code _n} of {@code U()} on input.
     * Its contents will be destructively modified.
     * Returns the energy of the vector (the sum of squares).
     */
    private static float cwrsi(int _n, int _k, int _i, int[] _y, int[] _u) {
        celt_assert(_n > 0);
        float yy = 0;
        for (int j = 0; j < _n; j++) {
            int p = _u[_k + 1];
            int s = Integer.compareUnsigned(_i, p) >= 0 ? -1 : 0;
            _i -= p & s;
            int yj = _k;
            p = _u[_k];
            while (Integer.compareUnsigned(p, _i) > 0) {
                p = _u[--_k];
            }
            _i -= p;
            yj -= _k;
            _y[j] = (yj + s) ^ s;
            yy += (float) _y[j] * (float) _y[j];
            uprev(_u, _k + 2);
        }
        return yy;
    }

    /**
     * Compute {@code V(_n,_k)}, as well as {@code U(_n,0..._k+1)}.
     * {@code _u}: On exit, {@code _u[i]} contains {@code U(_n,i)} for {@code i} in {@code [0..._k+1]}.
     */
    private static int ncwrs_urow(int _n, int _k, int[] _u) {
        int len = _k + 2;
        // We require storage at least 3 values (e.g., _k>0).
        celt_assert(len >= 3);
        _u[0] = 0;
        _u[1] = 1;
        // If _n==0, _u[0] should be 1 and the rest should be 0.
        // If _n==1, _u[i] should be 1 for i>1.
        celt_assert(_n >= 2);
        // If _k==0, the following loop will overflow the buffer.
        celt_assert(_k > 0);
        for (int k = 2; k < len; k++) {
            _u[k] = (k << 1) - 1;
        }
        for (int k = 2; k < _n; k++) {
            unext(_u, _k + 1);
        }
        return _u[_k] + _u[_k + 1];
    }

    /**
     * Computes the previous row/column of any recurrence that obeys the relation
     * {@code u[i-1][j]=u[i][j]-u[i][j-1]-u[i-1][j-1]}.
     * The base case for the new row/column is 0.
     */
    private static void uprev(int[] _ui, int _n) {
        // This loop will overrun the array if we don't have storage for at least 2 values.
        int ui0 = 0;
        for (int j = 1; j < _n; j++) {
            int ui1 = _ui[j] - _ui[j - 1] - ui0;
            _ui[j - 1] = ui0;
            ui0 = ui1;
        }
        _ui[_n - 1] = ui0;
    }

    /**
     * Computes the next row/column of any recurrence that obeys the relation
     * {@code u[i][j]=u[i-1][j]+u[i][j-1]+u[i-1][j-1]}.
     * The base case for the new row/column is 1.
     */
    private static void unext(int[] _ui, int _len) {
        // This loop will overrun the array if we don't have storage for at least 2 values.
        int ui0 = 1;
        for (int j = 1; j < _len; j++) {
            int ui1 = _ui[j + 1] + _ui[j] + ui0;
            _ui[j] = ui0;
            ui0 = ui1;
        }
        _ui[_len] = ui0;
    }
}
