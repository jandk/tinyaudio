package be.twofold.tinyaudio.opus.celt;

import static be.twofold.tinyaudio.opus.celt.Utils.celt_assert;

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
