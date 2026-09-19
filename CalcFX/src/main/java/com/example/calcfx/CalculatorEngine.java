package com.example.calcfx;

public class CalculatorEngine {

    public static double evaluate(String expression) {
        return parseAddSub(expression.trim());
    }

    private static double parseAddSub(String expr) {
        int i = findLastOf(expr, '+', '-');
        if (i > 0) {
            double left  = parseAddSub(expr.substring(0, i));
            double right = parseMulDiv(expr.substring(i + 1));
            return expr.charAt(i) == '+' ? left + right : left - right;
        }
        return parseMulDiv(expr);
    }

    private static double parseMulDiv(String expr) {
        int i = findLastOf(expr, '*', '/');
        if (i > 0) {
            double left  = parseMulDiv(expr.substring(0, i));
            double right = parsePow(expr.substring(i + 1));
            if (expr.charAt(i) == '/') {
                if (right == 0) throw new ArithmeticException("Division by zero");
                return left / right;
            }
            return left * right;
        }
        return parsePow(expr);
    }

    private static double parsePow(String expr) {
        int i = expr.lastIndexOf('^');
        if (i > 0) {
            double base = parsePow(expr.substring(0, i));
            double exp  = parsePow(expr.substring(i + 1));
            return Math.pow(base, exp);
        }
        return Double.parseDouble(expr.trim());
    }

    private static int findLastOf(String expr, char a, char b) {
        for (int i = expr.length() - 1; i >= 1; i--) {
            char c = expr.charAt(i);
            if ((c == a || c == b)) return i;
        }
        return -1;
    }
}