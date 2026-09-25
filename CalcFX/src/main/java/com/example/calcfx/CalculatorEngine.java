package com.example.calcfx;

import java.util.*;

public class CalculatorEngine {

    public enum AngleMode { DEGREES, RADIANS }

    private static final Set<String> FUNCTIONS = Set.of("sin", "cos", "tan", "log", "ln", "√");

    public static double evaluate(String expression, AngleMode angleMode) {
        String expr = expression.trim();
        if (expr.isEmpty()) return 0;

        // Auto-close any parentheses the user forgot to close
        long open = expr.chars().filter(c -> c == '(').count();
        long close = expr.chars().filter(c -> c == ')').count();
        expr += ")".repeat((int) Math.max(0, open - close));

        List<String> tokens = tokenize(expr);
        List<String> rpn = toRpn(tokens);
        return evalRpn(rpn, angleMode);
    }

    // ---------- Tokenizer ----------

    private static List<String> tokenize(String expr) {
        List<String> tokens = new ArrayList<>();
        int i = 0;
        while (i < expr.length()) {
            char c = expr.charAt(i);

            if (Character.isWhitespace(c)) { i++; continue; }

            if (Character.isDigit(c) || c == '.') {
                int start = i;
                while (i < expr.length() && (Character.isDigit(expr.charAt(i)) || expr.charAt(i) == '.')) i++;
                tokens.add(expr.substring(start, i));
                continue;
            }
            if (c == 'π') { tokens.add(String.valueOf(Math.PI)); i++; continue; }
            if (matches(expr, i, "sin")) { tokens.add("sin"); i += 3; continue; }
            if (matches(expr, i, "cos")) { tokens.add("cos"); i += 3; continue; }
            if (matches(expr, i, "tan")) { tokens.add("tan"); i += 3; continue; }
            if (matches(expr, i, "log")) { tokens.add("log"); i += 3; continue; }
            if (matches(expr, i, "ln"))  { tokens.add("ln");  i += 2; continue; }
            if (c == 'e') { tokens.add(String.valueOf(Math.E)); i++; continue; }
            if (c == '√') { tokens.add("√"); i++; continue; }
            if (c == '(' || c == ')' || c == '^' || c == '%' || c == '+') {
                tokens.add(String.valueOf(c)); i++; continue;
            }
            if (c == '−' || c == '-') { tokens.add("-"); i++; continue; }
            if (c == '×' || c == '*') { tokens.add("×"); i++; continue; }
            if (c == '÷' || c == '/') { tokens.add("÷"); i++; continue; }

            throw new IllegalArgumentException("Unexpected character: " + c);
        }
        return tokens;
    }

    private static boolean matches(String s, int i, String word) {
        return s.regionMatches(i, word, 0, word.length());
    }

    // ---------- Shunting-yard ----------

    private static boolean isNumber(String tok) {
        char c0 = tok.charAt(0);
        return Character.isDigit(c0) || c0 == '.';
    }

    private static boolean isFunction(String tok) { return FUNCTIONS.contains(tok); }

    private static boolean isOperator(String tok) {
        return tok.equals("+") || tok.equals("-") || tok.equals("×")
                || tok.equals("÷") || tok.equals("^") || tok.equals("u-");
    }

    private static int precedence(String op) {
        return switch (op) {
            case "+", "-" -> 1;
            case "×", "÷" -> 2;
            case "u-" -> 3;
            case "^" -> 4;
            default -> 0;
        };
    }

    private static boolean isRightAssoc(String op) { return op.equals("^") || op.equals("u-"); }

    private static List<String> toRpn(List<String> tokens) {
        List<String> output = new ArrayList<>();
        Deque<String> stack = new ArrayDeque<>();
        String prev = null;

        for (String tok : tokens) {
            if (isNumber(tok)) {
                output.add(tok);
            } else if (isFunction(tok)) {
                stack.push(tok);
            } else if (tok.equals("%")) {
                output.add(tok); // postfix: applies immediately to whatever precedes it
            } else if (tok.equals("-") && (prev == null || isOperator(prev) || prev.equals("(") || isFunction(prev))) {
                stack.push("u-"); // unary minus — just push, don't pop anything first
            } else if (isOperator(tok)) {
                while (!stack.isEmpty() && isOperator(stack.peek()) &&
                        (precedence(stack.peek()) > precedence(tok) ||
                                (precedence(stack.peek()) == precedence(tok) && !isRightAssoc(tok)))) {
                    output.add(stack.pop());
                }
                stack.push(tok);
            } else if (tok.equals("(")) {
                stack.push(tok);
            } else if (tok.equals(")")) {
                while (!stack.isEmpty() && !stack.peek().equals("(")) output.add(stack.pop());
                if (!stack.isEmpty()) stack.pop(); // discard "("
                if (!stack.isEmpty() && isFunction(stack.peek())) output.add(stack.pop());
            }
            prev = tok;
        }
        while (!stack.isEmpty()) output.add(stack.pop());
        return output;
    }

    // ---------- Evaluation ----------

    private static double evalRpn(List<String> rpn, AngleMode mode) {
        Deque<Double> stack = new ArrayDeque<>();
        for (String tok : rpn) {
            if (isNumber(tok)) {
                stack.push(Double.parseDouble(tok));
            } else if (tok.equals("u-")) {
                stack.push(-stack.pop());
            } else if (tok.equals("%")) {
                stack.push(stack.pop() / 100.0);
            } else if (isFunction(tok)) {
                double v = stack.pop();
                double angle = mode == AngleMode.DEGREES ? Math.toRadians(v) : v;
                double result = switch (tok) {
                    case "sin" -> Math.sin(angle);
                    case "cos" -> Math.cos(angle);
                    case "tan" -> Math.tan(angle);
                    case "log" -> Math.log10(v);
                    case "ln"  -> Math.log(v);
                    case "√"   -> Math.sqrt(v);
                    default -> v;
                };
                stack.push(result);
            } else {
                double b = stack.pop();
                double a = stack.pop();
                double result = switch (tok) {
                    case "+" -> a + b;
                    case "-" -> a - b;
                    case "×" -> a * b;
                    case "÷" -> {
                        if (b == 0) throw new ArithmeticException("Division by zero");
                        yield a / b;
                    }
                    case "^" -> Math.pow(a, b);
                    default -> throw new IllegalArgumentException("Unknown operator " + tok);
                };
                stack.push(result);
            }
        }
        if (stack.size() != 1) throw new IllegalArgumentException("Invalid expression");
        return stack.pop();
    }
}