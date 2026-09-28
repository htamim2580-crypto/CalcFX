package com.example.calcfx;

import java.math.BigDecimal;
import java.math.RoundingMode;


public class Matrix {

    private static final double EPS = 1e-9;

    private final int rows;
    private final int cols;
    private final double[][] data;

    public Matrix(double[][] source) {
        this.rows = source.length;
        this.cols = source[0].length;
        this.data = new double[rows][cols];
        for (int i = 0; i < rows; i++) {
            System.arraycopy(source[i], 0, data[i], 0, cols);
        }
    }

    public int rows() { return rows; }
    public int cols() { return cols; }

    /** Returns column j as an (rows x 1) matrix. */
    public Matrix column(int j) {
        double[][] out = new double[rows][1];
        for (int i = 0; i < rows; i++) out[i][0] = data[i][j];
        return new Matrix(out);
    }

    // ---------- Basic arithmetic ----------

    public Matrix add(Matrix o) {
        requireSameSize(o);
        double[][] out = new double[rows][cols];
        for (int i = 0; i < rows; i++)
            for (int j = 0; j < cols; j++) out[i][j] = data[i][j] + o.data[i][j];
        return new Matrix(out);
    }

    public Matrix subtract(Matrix o) {
        requireSameSize(o);
        double[][] out = new double[rows][cols];
        for (int i = 0; i < rows; i++)
            for (int j = 0; j < cols; j++) out[i][j] = data[i][j] - o.data[i][j];
        return new Matrix(out);
    }

    public Matrix multiply(Matrix o) {
        if (cols != o.rows) {
            throw new IllegalArgumentException("A×B needs columns of A = rows of B");
        }
        double[][] out = new double[rows][o.cols];
        for (int i = 0; i < rows; i++)
            for (int j = 0; j < o.cols; j++) {
                double sum = 0;
                for (int k = 0; k < cols; k++) sum += data[i][k] * o.data[k][j];
                out[i][j] = sum;
            }
        return new Matrix(out);
    }

    public Matrix scale(double k) {
        double[][] out = new double[rows][cols];
        for (int i = 0; i < rows; i++)
            for (int j = 0; j < cols; j++) out[i][j] = data[i][j] * k;
        return new Matrix(out);
    }

    public Matrix transpose() {
        double[][] out = new double[cols][rows];
        for (int i = 0; i < rows; i++)
            for (int j = 0; j < cols; j++) out[j][i] = data[i][j];
        return new Matrix(out);
    }

    public double trace() {
        requireSquare("Trace");
        double sum = 0;
        for (int i = 0; i < rows; i++) sum += data[i][i];
        return sum;
    }

    // ---------- Elimination-based operations ----------

    public double determinant() {
        requireSquare("Determinant");
        double[][] m = copyData();
        int n = rows;
        double det = 1;
        for (int c = 0; c < n; c++) {
            int p = c;
            for (int i = c + 1; i < n; i++) {
                if (Math.abs(m[i][c]) > Math.abs(m[p][c])) p = i;
            }
            if (Math.abs(m[p][c]) < EPS) return 0;
            if (p != c) {
                double[] tmp = m[p]; m[p] = m[c]; m[c] = tmp;
                det = -det; // each row swap flips the sign
            }
            det *= m[c][c];
            for (int i = c + 1; i < n; i++) {
                double f = m[i][c] / m[c][c];
                for (int j = c; j < n; j++) m[i][j] -= f * m[c][j];
            }
        }
        return det;
    }

    /** Gauss-Jordan on [A | I]. */
    public Matrix inverse() {
        requireSquare("Inverse");
        int n = rows;
        double[][] m = new double[n][2 * n];
        for (int i = 0; i < n; i++) {
            System.arraycopy(data[i], 0, m[i], 0, n);
            m[i][n + i] = 1;
        }
        for (int c = 0; c < n; c++) {
            int p = c;
            for (int i = c + 1; i < n; i++) {
                if (Math.abs(m[i][c]) > Math.abs(m[p][c])) p = i;
            }
            if (Math.abs(m[p][c]) < EPS) {
                throw new ArithmeticException("Matrix is singular — no inverse exists");
            }
            double[] tmp = m[p]; m[p] = m[c]; m[c] = tmp;
            double pv = m[c][c];
            for (int j = 0; j < 2 * n; j++) m[c][j] /= pv;
            for (int i = 0; i < n; i++) {
                if (i == c) continue;
                double f = m[i][c];
                if (f == 0.0) continue;
                for (int j = 0; j < 2 * n; j++) m[i][j] -= f * m[c][j];
            }
        }
        double[][] out = new double[n][n];
        for (int i = 0; i < n; i++) System.arraycopy(m[i], n, out[i], 0, n);
        clean(out);
        return new Matrix(out);
    }

    public Matrix rref() {
        double[][] m = copyData();
        reduce(m, cols);
        clean(m);
        return new Matrix(m);
    }

    public int rank() {
        double[][] m = copyData();
        return reduce(m, cols);
    }

    /** Solves A x = b for a column vector b. Handles unique / none / infinite solutions. */
    public Matrix solve(Matrix b) {
        if (b.cols != 1 || b.rows != rows) {
            throw new IllegalArgumentException("b must have the same number of rows as A");
        }
        int n = cols;
        double[][] m = new double[rows][n + 1];
        for (int i = 0; i < rows; i++) {
            System.arraycopy(data[i], 0, m[i], 0, n);
            m[i][n] = b.data[i][0];
        }
        int rank = reduce(m, n); // pivot only within A's columns, not the b column
        for (int i = rank; i < rows; i++) {
            if (Math.abs(m[i][n]) > EPS) {
                throw new ArithmeticException("No solution — the system is inconsistent");
            }
        }
        if (rank < n) {
            throw new ArithmeticException("Infinitely many solutions (rank " + rank + " < " + n + " unknowns)");
        }
        double[][] x = new double[n][1];
        for (int i = 0; i < n; i++) x[i][0] = m[i][n];
        clean(x);
        return new Matrix(x);
    }

    /**
     * In-place Gauss-Jordan elimination. Pivots are searched only in the first
     * pivotCols columns, but row operations apply across the full width.
     * Returns the number of pivots found (the rank of that left part).
     */
    private static int reduce(double[][] m, int pivotCols) {
        int rows = m.length;
        int width = m[0].length;
        int r = 0;
        for (int c = 0; c < pivotCols && r < rows; c++) {
            int p = r;
            for (int i = r + 1; i < rows; i++) {
                if (Math.abs(m[i][c]) > Math.abs(m[p][c])) p = i;
            }
            if (Math.abs(m[p][c]) < EPS) continue;
            double[] tmp = m[p]; m[p] = m[r]; m[r] = tmp;
            double pv = m[r][c];
            for (int j = 0; j < width; j++) m[r][j] /= pv;
            for (int i = 0; i < rows; i++) {
                if (i == r) continue;
                double f = m[i][c];
                if (f == 0.0) continue;
                for (int j = 0; j < width; j++) m[i][j] -= f * m[r][j];
            }
            r++;
        }
        return r;
    }

    // ---------- Helpers ----------

    private void requireSquare(String what) {
        if (rows != cols) throw new IllegalArgumentException(what + " needs a square matrix");
    }

    private void requireSameSize(Matrix o) {
        if (rows != o.rows || cols != o.cols) {
            throw new IllegalArgumentException("Matrices must be the same size");
        }
    }

    private double[][] copyData() {
        double[][] copy = new double[rows][cols];
        for (int i = 0; i < rows; i++) System.arraycopy(data[i], 0, copy[i], 0, cols);
        return copy;
    }

    private static void clean(double[][] m) {
        for (double[] row : m)
            for (int j = 0; j < row.length; j++)
                if (Math.abs(row[j]) < EPS) row[j] = 0;
    }

    // ---------- Formatting ----------

    /** Rounds to 6 decimals and strips trailing zeros: 2.500000 -> "2.5", 3.0 -> "3". */
    public static String format(double v) {
        if (!Double.isFinite(v)) return "Error";
        if (Math.abs(v) < EPS) return "0";
        return new BigDecimal(v).setScale(6, RoundingMode.HALF_UP)
                .stripTrailingZeros().toPlainString();
    }

    /** Column-aligned multi-line display, meant for a monospace font. */
    @Override
    public String toString() {
        String[][] s = new String[rows][cols];
        int w = 1;
        for (int i = 0; i < rows; i++)
            for (int j = 0; j < cols; j++) {
                s[i][j] = format(data[i][j]);
                w = Math.max(w, s[i][j].length());
            }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < rows; i++) {
            sb.append("[ ");
            for (int j = 0; j < cols; j++) {
                if (j > 0) sb.append("  ");
                sb.append(String.format("%" + w + "s", s[i][j]));
            }
            sb.append(" ]");
            if (i < rows - 1) sb.append('\n');
        }
        return sb.toString();
    }

    /** Compact single-line form for history, e.g. [1 2; 3 4]. */
    public String toInline() {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < rows; i++) {
            if (i > 0) sb.append("; ");
            for (int j = 0; j < cols; j++) {
                if (j > 0) sb.append(' ');
                sb.append(format(data[i][j]));
            }
        }
        return sb.append(']').toString();
    }
}