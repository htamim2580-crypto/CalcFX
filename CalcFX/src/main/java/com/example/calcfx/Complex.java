package com.example.calcfx;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

// Immutable complex number a + bi with the standard operations.
public record Complex(double re, double im) {

    private static final double EPS = 1e-9;
    public static final Complex ONE = new Complex(1, 0);

    // ---------- Arithmetic ----------

    public Complex plus(Complex o) { return new Complex(re + o.re, im + o.im); }

    public Complex minus(Complex o) { return new Complex(re - o.re, im - o.im); }

    public Complex times(Complex o) {
        return new Complex(re * o.re - im * o.im, re * o.im + im * o.re);
    }

    public Complex dividedBy(Complex o) {
        if (o.re == 0 && o.im == 0) throw new ArithmeticException("Division by zero");
        double d = o.re * o.re + o.im * o.im;
        return new Complex((re * o.re + im * o.im) / d, (im * o.re - re * o.im) / d);
    }

    public Complex conjugate() { return new Complex(re, -im); }

    public Complex reciprocal() { return ONE.dividedBy(this); }

    // ---------- Polar properties ----------

    public double modulus() { return Math.hypot(re, im); }

    /** Principal argument in radians, in (-π, π]. */
    public double argument() { return Math.atan2(im, re); }

    // ---------- Powers and roots ----------

    /** Principal square root. */
    public Complex sqrt() {
        double m = modulus();
        double sr = Math.sqrt((m + re) / 2);
        double si = Math.sqrt(Math.max(0, (m - re) / 2));
        return new Complex(sr, im < 0 ? -si : si);
    }

    /** Integer power by repeated squaring (exact for integer inputs). */
    public Complex pow(int n) {
        if (n == 0) return ONE;
        Complex base = n > 0 ? this : reciprocal();
        long e = Math.abs((long) n);
        Complex result = ONE;
        Complex b = base;
        while (e > 0) {
            if ((e & 1) == 1) result = result.times(b);
            b = b.times(b);
            e >>= 1;
        }
        if (!Double.isFinite(result.re) || !Double.isFinite(result.im)) {
            throw new ArithmeticException("Result is too large");
        }
        return result;
    }

    /** All n distinct nth roots (De Moivre's theorem), in order k = 0 .. n-1. */
    public List<Complex> roots(int n) {
        if (n < 1) throw new IllegalArgumentException("n must be at least 1");
        List<Complex> out = new ArrayList<>();
        double r = Math.pow(modulus(), 1.0 / n);
        double theta = argument();
        for (int k = 0; k < n; k++) {
            double angle = (theta + 2 * Math.PI * k) / n;
            out.add(new Complex(r * Math.cos(angle), r * Math.sin(angle)).cleaned());
        }
        return out;
    }

    /** Snaps floating-point dust (like 1e-16) to exactly zero. */
    private Complex cleaned() {
        double scale = Math.max(1, Math.max(Math.abs(re), Math.abs(im)));
        double r = Math.abs(re) < EPS * scale ? 0 : re;
        double i = Math.abs(im) < EPS * scale ? 0 : im;
        return new Complex(r, i);
    }

    // ---------- Formatting ----------

    /** Rounds to 6 decimals and strips trailing zeros: 2.500000 -> "2.5". */
    public static String format(double v) {
        if (!Double.isFinite(v)) return "Error";
        if (Math.abs(v) < EPS) return "0";
        return new BigDecimal(v).setScale(6, RoundingMode.HALF_UP)
                .stripTrailingZeros().toPlainString();
    }

    /** Human form: "3 + 4i", "-2i", "i", "5". */
    @Override
    public String toString() {
        String r = format(re);
        String i = format(im);
        if (i.equals("0")) return r;
        boolean neg = i.startsWith("-");
        String mag = neg ? i.substring(1) : i;
        String coeff = mag.equals("1") ? "i" : mag + "i";
        if (r.equals("0")) return (neg ? "-" : "") + coeff;
        return r + (neg ? " - " : " + ") + coeff;
    }

    /** Polar description, showing the angle in both radians and degrees. */
    public String toPolar() {
        if (re == 0 && im == 0) return "r = 0\nθ is undefined for 0";
        double theta = argument();
        return "r = " + format(modulus())
                + "\nθ = " + format(theta) + " rad (" + format(Math.toDegrees(theta)) + "°)";
    }
}