package com.example.calcfx;

import java.util.function.DoubleUnaryOperator;
import javafx.scene.paint.Color;
import net.objecthunter.exp4j.Expression;
import net.objecthunter.exp4j.ExpressionBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleBinaryOperator;


public class GraphEngine {

    public record Pt(double x, double y) {}


    public record PlotSeries(Color color, List<double[]> segments) {}


    public static class Equation {
        private final Expression lhs;
        private final Expression rhs;

        private Equation(Expression lhs, Expression rhs) {
            this.lhs = lhs;
            this.rhs = rhs;
        }

        public double diff(double x, double y) {
            try {
                lhs.setVariable("x", x).setVariable("y", y);
                rhs.setVariable("x", x).setVariable("y", y);
                double d = lhs.evaluate() - rhs.evaluate();
                return Double.isFinite(d) ? d : Double.NaN;
            } catch (Exception e) {
                return Double.NaN;
            }
        }
    }


    public static Equation parseEquation(String raw) {
        String text = raw.trim();
        if (text.isEmpty()) throw new IllegalArgumentException("Empty expression");

        String lhsText, rhsText;
        int eq = text.indexOf('=');
        if (eq < 0) {
            lhsText = "y";
            rhsText = text;
        } else {
            lhsText = text.substring(0, eq);
            rhsText = text.substring(eq + 1);
        }

        Expression lhs = new ExpressionBuilder(normalize(lhsText)).variables("x", "y").build();
        Expression rhs = new ExpressionBuilder(normalize(rhsText)).variables("x", "y").build();
        return new Equation(lhs, rhs);
    }


    public static List<double[]> traceImplicit(DoubleBinaryOperator fn,
                                               double xMin, double xMax, double yMin, double yMax,
                                               int cols, int rows) {
        List<double[]> segments = new ArrayList<>();
        double dx = (xMax - xMin) / cols;
        double dy = (yMax - yMin) / rows;


        double[][] grid = new double[rows + 1][cols + 1];
        for (int j = 0; j <= rows; j++) {
            double y = yMin + j * dy;
            for (int i = 0; i <= cols; i++) {
                grid[j][i] = fn.applyAsDouble(xMin + i * dx, y);
            }
        }

        for (int j = 0; j < rows; j++) {
            double y0 = yMin + j * dy, y1 = yMin + (j + 1) * dy;
            for (int i = 0; i < cols; i++) {
                double x0 = xMin + i * dx, x1 = xMin + (i + 1) * dx;
                double v00 = grid[j][i], v10 = grid[j][i + 1], v11 = grid[j + 1][i + 1], v01 = grid[j + 1][i];
                if (!Double.isFinite(v00) || !Double.isFinite(v10) || !Double.isFinite(v11) || !Double.isFinite(v01))
                    continue;

                boolean b00 = v00 >= 0, b10 = v10 >= 0, b11 = v11 >= 0, b01 = v01 >= 0;
                int mask = (b00 ? 1 : 0) | (b10 ? 2 : 0) | (b11 ? 4 : 0) | (b01 ? 8 : 0);
                if (mask == 0 || mask == 15) continue;

                Pt bottom = b00 != b10 ? interp(x0, y0, v00, x1, y0, v10) : null;
                Pt right  = b10 != b11 ? interp(x1, y0, v10, x1, y1, v11) : null;
                Pt top    = b11 != b01 ? interp(x1, y1, v11, x0, y1, v01) : null;
                Pt left   = b01 != b00 ? interp(x0, y1, v01, x0, y0, v00) : null;

                switch (mask) {
                    case 1, 14 -> addSeg(segments, left, bottom);
                    case 2, 13 -> addSeg(segments, bottom, right);
                    case 3, 12 -> addSeg(segments, left, right);
                    case 4, 11 -> addSeg(segments, right, top);
                    case 6, 9  -> addSeg(segments, bottom, top);
                    case 7, 8  -> addSeg(segments, left, top);
                    case 5     -> { addSeg(segments, left, bottom); addSeg(segments, right, top); }
                    case 10    -> { addSeg(segments, bottom, right); addSeg(segments, left, top); }
                    default -> {}
                }
            }
        }
        return segments;
    }

    private static void addSeg(List<double[]> list, Pt a, Pt b) {
        if (a != null && b != null) list.add(new double[]{a.x(), a.y(), b.x(), b.y()});
    }

    private static Pt interp(double x1, double y1, double v1, double x2, double y2, double v2) {
        double t = v1 / (v1 - v2);
        return new Pt(x1 + t * (x2 - x1), y1 + t * (y2 - y1));
    }

    private static String normalize(String expr) {
        String s = expr.trim();
        if (s.isEmpty()) s = "0";

        s = s.replace("×", "*").replace("÷", "/").replace("−", "-")
                .replace("π", "pi").replace("√", "sqrt");

        // Let people type "sinx" / "cosy" without parentheses
        s = s.replaceAll("\\b(sin|cos|tan|asin|acos|atan|log|ln|sqrt|abs)(x|y)\\b", "$1($2)");

        // Implicit multiplication: "2x" -> "2*x", "2(" -> "2*(", ")(" -> ")*(", "x(" -> "x*("
        s = s.replaceAll("(\\d)\\s*(x|y|pi|e)\\b", "$1*$2");
        s = s.replaceAll("(\\d)\\s*\\(", "$1*(");
        s = s.replaceAll("\\)\\s*(\\d|x|y|pi|e|\\()", ")*$1");
        s = s.replaceAll("\\b(x|y)\\s*\\(", "$1*(");

        return s;
    }

    public static DoubleUnaryOperator parseFunctionOfX(String raw) {
        Expression expr = new ExpressionBuilder(normalize(raw)).variables("x", "y").build();
        return x -> {
            try {
                expr.setVariable("x", x);
                expr.setVariable("y", 0);
                double v = expr.evaluate();
                return Double.isFinite(v) ? v : Double.NaN;
            } catch (Exception e) {
                return Double.NaN;
            }
        };
    }
}
