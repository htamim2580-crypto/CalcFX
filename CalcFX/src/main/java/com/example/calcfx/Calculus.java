package com.example.calcfx;

import java.util.function.DoubleUnaryOperator;


public class Calculus {

    private static final double DERIVATIVE_H = 1e-5;
    private static final int DEFAULT_INTEGRATION_STEPS = 1000;

    /** Central difference method: f'(x) ≈ (f(x+h) - f(x-h)) / (2h). Error shrinks as O(h²). */
    public static double differentiate(DoubleUnaryOperator f, double x) {
        double h = DERIVATIVE_H;
        double fPlus = f.applyAsDouble(x + h);
        double fMinus = f.applyAsDouble(x - h);
        if (!Double.isFinite(fPlus) || !Double.isFinite(fMinus)) {
            throw new ArithmeticException("Function is undefined near x = " + x);
        }
        return (fPlus - fMinus) / (2 * h);
    }

    // Composite Simpson's 1/3 Rule over n subintervals (rounded up to even)
    public static double integrate(DoubleUnaryOperator f, double a, double b, int n) {
        if (n % 2 != 0) n++;
        double h = (b - a) / n;
        double sum = f.applyAsDouble(a) + f.applyAsDouble(b);
        for (int i = 1; i < n; i++) {
            double x = a + i * h;
            double fx = f.applyAsDouble(x);
            if (!Double.isFinite(fx)) {
                throw new ArithmeticException("Function is undefined at x = " + x);
            }
            sum += (i % 2 == 0 ? 2 : 4) * fx;
        }
        return sum * h / 3.0;
    }

    public static double integrate(DoubleUnaryOperator f, double a, double b) {
        return integrate(f, a, b, DEFAULT_INTEGRATION_STEPS);
    }
}