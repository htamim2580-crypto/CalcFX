package com.example.calcfx;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;

public class HelloController {

    @FXML private Label expressionLabel;
    @FXML private Label resultLabel;

    private StringBuilder expression = new StringBuilder();
    private boolean justCalculated = false;
    private String pendingFunc = null; // prefix-mode function waiting for a number

    @FXML
    protected void onNumber(ActionEvent event) {
        String digit = ((Button) event.getSource()).getText();
        if (justCalculated && pendingFunc == null) { expression.setLength(0); justCalculated = false; }
        else if (justCalculated) { justCalculated = false; }
        expression.append(digit);
        resultLabel.setText(pendingFunc != null
                ? funcPrefix(pendingFunc) + expression + ")" : expression.toString());
    }

    @FXML
    protected void onOperator(ActionEvent event) {
        String op = ((Button) event.getSource()).getText();
        if (pendingFunc != null) { applyFuncInternal(pendingFunc); pendingFunc = null; }
        justCalculated = false;
        switch (op) {
            case "÷" -> expression.append("/");
            case "×" -> expression.append("*");
            case "−" -> expression.append("-");
            default  -> expression.append(op);
        }
        expressionLabel.setText(toDisplay(expression.toString()));
        resultLabel.setText(toDisplay(expression.toString()));
    }

    @FXML
    protected void onEquals(ActionEvent event) {
        try {
            if (pendingFunc != null) {
                String func = pendingFunc; pendingFunc = null;
                String numStr = expression.length() > 0 ? expression.toString() : "0";
                expressionLabel.setText(funcPrefix(func) + numStr + ") =");
                applyFuncWithValue(func, numStr);
            } else {
                expressionLabel.setText(toDisplay(expression.toString()) + " =");
                double result = CalculatorEngine.evaluate(expression.toString());
                String formatted = formatResult(result);
                resultLabel.setText(formatted);
                expression = new StringBuilder(formatted);
                justCalculated = true;
            }
        } catch (Exception e) {
            resultLabel.setText("Error");
            expression.setLength(0); pendingFunc = null;
        }
    }

    @FXML protected void onClear() {
        expression.setLength(0); expressionLabel.setText("");
        resultLabel.setText("0"); justCalculated = false; pendingFunc = null;
    }

    @FXML protected void onBackspace() {
        if (pendingFunc != null && expression.length() == 0) {
            pendingFunc = null; expressionLabel.setText(""); resultLabel.setText("0"); return;
        }
        if (expression.length() > 0) {
            expression.deleteCharAt(expression.length() - 1);
            String base = expression.length() > 0 ? toDisplay(expression.toString()) : "0";
            resultLabel.setText(pendingFunc != null && expression.length() > 0
                    ? funcPrefix(pendingFunc) + expression + ")" : base);
        }
    }

    @FXML protected void onDot() {
        if (justCalculated) { expression.setLength(0); expression.append("0"); justCalculated = false; }
        String expr = expression.toString();
        int lastOp = Math.max(Math.max(expr.lastIndexOf('+'), expr.lastIndexOf('*')),
                Math.max(expr.lastIndexOf('/'), expr.lastIndexOf('^')));
        for (int i = expr.length() - 1; i > 0; i--) {
            if (expr.charAt(i) == '-') { lastOp = Math.max(lastOp, i); break; }
        }
        String current = expr.substring(lastOp + 1);
        if (!current.contains(".")) {
            if (current.isEmpty()) expression.append("0");
            expression.append(".");
            resultLabel.setText(pendingFunc != null
                    ? funcPrefix(pendingFunc) + expression + ")"
                    : toDisplay(expression.toString()));
        }
    }

    @FXML protected void onSin()    { handleFunc("sin");    }
    @FXML protected void onCos()    { handleFunc("cos");    }
    @FXML protected void onTan()    { handleFunc("tan");    }
    @FXML protected void onLog()    { handleFunc("log");    }
    @FXML protected void onLn()     { handleFunc("ln");     }
    @FXML protected void onSqrt()   { handleFunc("sqrt");   }
    @FXML protected void onSquare() { handleFunc("square"); }

    private void handleFunc(String func) {
        if (expression.length() == 0) {
            // PREFIX MODE — no number yet, wait for it
            pendingFunc = func;
            expressionLabel.setText(funcPrefix(func) + "...");
            resultLabel.setText(funcPrefix(func));
        } else {
            // POSTFIX MODE — number already typed, apply now
            applyFuncInternal(func);
        }
    }

    @FXML protected void onPower() {
        if (expression.length() == 0) expression.append("0");
        expression.append("^");
        expressionLabel.setText(toDisplay(expression.toString()));
        resultLabel.setText(toDisplay(expression.toString()));
        justCalculated = false;
    }

    @FXML protected void onPi() {
        if (justCalculated) { expression.setLength(0); justCalculated = false; }
        if (expression.length() > 0) {
            char last = expression.charAt(expression.length() - 1);
            if (Character.isDigit(last) || last == '.') expression.append("*");
        }
        expression.append(Math.PI);
        resultLabel.setText(pendingFunc != null
                ? funcPrefix(pendingFunc) + toDisplay(expression.toString()) + ")"
                : toDisplay(expression.toString()));
    }

    @FXML protected void onE() {
        if (justCalculated) { expression.setLength(0); justCalculated = false; }
        if (expression.length() > 0) {
            char last = expression.charAt(expression.length() - 1);
            if (Character.isDigit(last) || last == '.') expression.append("*");
        }
        expression.append(Math.E);
        resultLabel.setText(pendingFunc != null
                ? funcPrefix(pendingFunc) + toDisplay(expression.toString()) + ")"
                : toDisplay(expression.toString()));
    }

    private void applyFuncInternal(String func) { applyFuncWithValue(func, getCurrentNumber()); }

    private void applyFuncWithValue(String func, String numStr) {
        try {
            double val = Double.parseDouble(numStr);
            double result = switch (func) {
                case "sin"    -> Math.sin(Math.toRadians(val));
                case "cos"    -> Math.cos(Math.toRadians(val));
                case "tan"    -> Math.tan(Math.toRadians(val));
                case "log"    -> Math.log10(val);
                case "ln"     -> Math.log(val);
                case "sqrt"   -> Math.sqrt(val);
                case "square" -> val * val;
                default -> val;
            };
            expressionLabel.setText(funcPrefix(func) + numStr + ") =");
            String formatted = formatResult(result);
            resultLabel.setText(formatted);
            expression = new StringBuilder(formatted);
            justCalculated = true;
        } catch (NumberFormatException e) {
            resultLabel.setText("Error"); expression.setLength(0);
        }
    }

    private String getCurrentNumber() {
        String expr = expression.toString();
        if (expr.isEmpty()) return "0";
        int lastOp = -1;
        for (int i = expr.length() - 1; i >= 0; i--) {
            char c = expr.charAt(i);
            if (c == '+' || c == '*' || c == '/' || c == '^') { lastOp = i; break; }
            if (c == '-' && i > 0) { lastOp = i; break; }
        }
        String num = expr.substring(lastOp + 1);
        return num.isEmpty() ? "0" : num;
    }

    private String funcPrefix(String func) {
        return switch (func) {
            case "sqrt"   -> "√(";
            case "square" -> "sq(";
            case "sin"    -> "sin(";
            case "cos"    -> "cos(";
            case "tan"    -> "tan(";
            case "log"    -> "log(";
            case "ln"     -> "ln(";
            default -> func + "(";
        };
    }

    private String toDisplay(String expr) {
        return expr.replace(String.valueOf(Math.PI), "π")
                .replace(String.valueOf(Math.E),  "e")
                .replace("*", "×").replace("/", "÷");
    }

    private String formatResult(double result) {
        if (Double.isNaN(result))      return "Error";
        if (Double.isInfinite(result)) return result > 0 ? "∞" : "-∞";
        double rounded = Math.round(result * 1e10) / 1e10;
        if (rounded == Math.floor(rounded) && Math.abs(rounded) < 1e15)
            return String.valueOf((long) rounded);
        return String.valueOf(rounded);
    }
}