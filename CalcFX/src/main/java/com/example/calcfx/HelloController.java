package com.example.calcfx;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;   // NEW
import javafx.scene.control.Label;
import javafx.scene.control.TextField;  // NEW
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.GridPane;    // NEW
import javafx.scene.layout.VBox;        // NEW

import java.util.Map;                   // NEW

public class HelloController {

    @FXML private Label statusLabel;
    @FXML private Label expressionLabel;
    @FXML private Label resultLabel;
    @FXML private Button angleModeButton;

    // NEW: currency converter controls
    @FXML private Button currencyToggle;
    @FXML private GridPane calculatorGrid;
    @FXML private VBox currencyPane;
    @FXML private TextField amountField;
    @FXML private ComboBox<String> fromCurrency;
    @FXML private ComboBox<String> toCurrency;
    @FXML private Label conversionResultLabel;
    @FXML private Label rateInfoLabel;

    private StringBuilder expression = new StringBuilder();
    private boolean justCalculated = false;
    private int openParens = 0;
    private double memory = 0;
    private CalculatorEngine.AngleMode angleMode = CalculatorEngine.AngleMode.DEGREES;

    // NEW
    private final CurrencyService currencyService = new CurrencyService();
    private Map<String, Double> rates;
    private boolean currencyMode = false;

    @FXML
    public void initialize() {
        updateStatus();
        // Attach keyboard support once the scene is available
        resultLabel.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) newScene.setOnKeyPressed(this::handleKeyPress);
        });

        // NEW: digits-only input for the amount field, and live conversion
        amountField.textProperty().addListener((obs, old, text) -> {
            if (!text.matches("\\d*(\\.\\d*)?")) {
                amountField.setText(old);
            } else {
                onConvert();
            }
        });
        fromCurrency.valueProperty().addListener((obs, o, n) -> onConvert());
        toCurrency.valueProperty().addListener((obs, o, n) -> onConvert());
    }

    // ---------- NEW: currency converter ----------

    @FXML
    protected void onToggleCurrency() {
        currencyMode = !currencyMode;
        currencyPane.setVisible(currencyMode);
        currencyPane.setManaged(currencyMode);
        calculatorGrid.setVisible(!currencyMode);
        calculatorGrid.setManaged(!currencyMode);
        currencyToggle.setText(currencyMode ? "🔢" : "💱");
        if (currencyMode && rates == null) loadRates();
    }

    private void loadRates() {
        conversionResultLabel.setText("Loading rates…");
        currencyService.fetchRates(newRates -> {
            rates = newRates;
            fromCurrency.getItems().setAll(rates.keySet());
            toCurrency.getItems().setAll(rates.keySet());
            fromCurrency.setValue("USD");
            toCurrency.setValue("BDT");
            conversionResultLabel.setText("");
        }, error -> {
            conversionResultLabel.setText("Couldn't load rates");
            rateInfoLabel.setText(error);
        });
    }

    @FXML
    protected void onSwapCurrencies() {
        String from = fromCurrency.getValue();
        fromCurrency.setValue(toCurrency.getValue());
        toCurrency.setValue(from);
    }

    @FXML
    protected void onConvert() {
        if (rates == null) return;
        String text = amountField.getText().trim();
        if (text.isEmpty() || text.equals(".")) {
            conversionResultLabel.setText("");
            rateInfoLabel.setText("");
            return;
        }
        String from = fromCurrency.getValue();
        String to = toCurrency.getValue();
        if (from == null || to == null) return;

        double amount = Double.parseDouble(text); // field only allows valid numbers
        // Rates are USD-based: convert FROM -> USD -> TO
        double result = amount / rates.get(from) * rates.get(to);
        conversionResultLabel.setText(formatResult(amount) + " " + from + " = " + formatResult(result) + " " + to);
        rateInfoLabel.setText("1 " + from + " = " + formatResult(rates.get(to) / rates.get(from)) + " " + to);
    }

    // ---------- Digits / dot ----------
    @FXML protected void onNumber(ActionEvent event) {
        appendText(((Button) event.getSource()).getText());
    }

    @FXML protected void onDot() {
        String expr = expression.toString();
        int i = expr.length() - 1;
        while (i >= 0 && (Character.isDigit(expr.charAt(i)) || expr.charAt(i) == '.')) i--;
        String currentNumber = expr.substring(i + 1);
        if (justCalculated) {
            expression.setLength(0);
            justCalculated = false;
            expression.append("0.");
            updateDisplay();
            return;
        }
        if (currentNumber.contains(".")) return;
        if (currentNumber.isEmpty()) expression.append("0");
        expression.append(".");
        updateDisplay();
    }

    // ---------- Operators ----------
    @FXML protected void onOperator(ActionEvent event) {
        appendOperatorChar(((Button) event.getSource()).getText().charAt(0));
    }

    @FXML protected void onPower() { appendOperatorChar('^'); }

    @FXML protected void onPercent() {
        if (expression.length() == 0) return;
        if (isValueChar(expression.charAt(expression.length() - 1))) {
            expression.append('%');
            updateDisplay();
        }
    }

    private void appendOperatorChar(char op) {
        justCalculated = false;
        if (expression.length() == 0) {
            if (op == '−') { expression.append(op); updateDisplay(); }
            return;
        }
        char last = expression.charAt(expression.length() - 1);
        if (isOperatorChar(last)) {
            expression.setCharAt(expression.length() - 1, op);
        } else {
            expression.append(op);
        }
        updateDisplay();
    }

    private boolean isOperatorChar(char c) {
        return c == '+' || c == '−' || c == '×' || c == '÷' || c == '^';
    }

    // ---------- Parentheses ----------
    @FXML protected void onOpenParen() {
        if (justCalculated) { expression.setLength(0); justCalculated = false; }
        maybeInsertImplicitMultiply("(");
        expression.append('(');
        openParens++;
        updateDisplay();
    }

    @FXML protected void onCloseParen() {
        if (openParens == 0 || expression.length() == 0) return;
        char last = expression.charAt(expression.length() - 1);
        if (isValueChar(last)) {
            expression.append(')');
            openParens--;
            updateDisplay();
        }
    }

    // ---------- Functions ----------
    @FXML protected void onSin()  { appendFunction("sin"); }
    @FXML protected void onCos()  { appendFunction("cos"); }
    @FXML protected void onTan()  { appendFunction("tan"); }
    @FXML protected void onLog()  { appendFunction("log"); }
    @FXML protected void onLn()   { appendFunction("ln"); }
    @FXML protected void onSqrt() { appendFunction("√"); }

    private void appendFunction(String name) {
        if (justCalculated) { expression.setLength(0); justCalculated = false; }
        maybeInsertImplicitMultiply(name);
        expression.append(name).append('(');
        openParens++;
        updateDisplay();
    }

    @FXML protected void onSquare() {
        if (expression.length() == 0) return;
        if (isValueChar(expression.charAt(expression.length() - 1))) {
            expression.append("^2");
            updateDisplay();
        }
    }

    // ---------- Constants ----------
    @FXML protected void onPi() { appendConstant("π"); }
    @FXML protected void onE()  { appendConstant("e"); }

    private void appendConstant(String symbol) {
        if (justCalculated) { expression.setLength(0); justCalculated = false; }
        maybeInsertImplicitMultiply(symbol);
        expression.append(symbol);
        updateDisplay();
    }

    // ---------- Clear / backspace ----------
    @FXML protected void onClear() {
        expression.setLength(0);
        openParens = 0;
        justCalculated = false;
        expressionLabel.setText("");
        resultLabel.setText("0");
    }

    @FXML protected void onBackspace() {
        if (expression.length() == 0) return;
        char removed = expression.charAt(expression.length() - 1);
        expression.deleteCharAt(expression.length() - 1);
        if (removed == '(') openParens--;
        if (removed == ')') openParens++;
        updateDisplay();
    }

    // ---------- Equals ----------
    @FXML protected void onEquals(ActionEvent event) {
        if (expression.length() == 0) return;
        try {
            double result = CalculatorEngine.evaluate(expression.toString(), angleMode);
            String formatted = formatResult(result);
            expressionLabel.setText(expression + " =");
            resultLabel.setText(formatted);
            expression = new StringBuilder(formatted);
            openParens = 0;
            justCalculated = true;
        } catch (Exception e) {
            resultLabel.setText("Error");
            expression.setLength(0);
            openParens = 0;
            justCalculated = false;
        }
    }

    // ---------- Memory ----------
    @FXML protected void onMemoryClear() { memory = 0; updateStatus(); }

    @FXML protected void onMemoryRecall() { appendText(formatResult(memory)); }

    @FXML protected void onMemoryAdd() {
        try { memory += CalculatorEngine.evaluate(expression.toString(), angleMode); } catch (Exception ignored) {}
        updateStatus();
    }

    @FXML protected void onMemorySubtract() {
        try { memory -= CalculatorEngine.evaluate(expression.toString(), angleMode); } catch (Exception ignored) {}
        updateStatus();
    }

    // ---------- Deg/Rad ----------
    @FXML protected void onToggleAngleMode() {
        angleMode = (angleMode == CalculatorEngine.AngleMode.DEGREES)
                ? CalculatorEngine.AngleMode.RADIANS
                : CalculatorEngine.AngleMode.DEGREES;
        angleModeButton.setText(angleMode == CalculatorEngine.AngleMode.DEGREES ? "Deg" : "Rad");
        updateStatus();
    }

    private void updateStatus() {
        String mode = angleMode == CalculatorEngine.AngleMode.DEGREES ? "DEG" : "RAD";
        statusLabel.setText(memory != 0 ? mode + "   M" : mode);
    }

    // ---------- Keyboard ----------
    private void handleKeyPress(KeyEvent event) {
        if (currencyMode) return; // NEW: don't hijack typing in the converter
        KeyCode code = event.getCode();
        if (code == KeyCode.ENTER)      { onEquals(null); return; }
        if (code == KeyCode.BACK_SPACE) { onBackspace();  return; }
        if (code == KeyCode.ESCAPE)     { onClear();      return; }
        String text = event.getText();
        if (text == null || text.isEmpty()) return;
        char c = text.charAt(0);
        if (Character.isDigit(c)) { appendText(String.valueOf(c)); return; }
        switch (c) {
            case '.' -> onDot();
            case '+' -> appendOperatorChar('+');
            case '-' -> appendOperatorChar('−');
            case '*' -> appendOperatorChar('×');
            case '/' -> appendOperatorChar('÷');
            case '^' -> appendOperatorChar('^');
            case '(' -> onOpenParen();
            case ')' -> onCloseParen();
            case '%' -> onPercent();
            case '=' -> onEquals(null);
            default -> {}
        }
    }

    // ---------- Shared helpers ----------
    private void appendText(String text) {
        if (justCalculated) {
            boolean startsNewNumber = Character.isDigit(text.charAt(0)) || text.equals(".") || text.charAt(0) == '-';
            if (startsNewNumber) expression.setLength(0);
            justCalculated = false;
        }
        maybeInsertImplicitMultiply(text);
        expression.append(text);
        updateDisplay();
    }

    private void maybeInsertImplicitMultiply(String next) {
        if (expression.length() == 0 || next.isEmpty()) return;
        char last = expression.charAt(expression.length() - 1);
        if (isValueChar(last) && isValueChar(next.charAt(0))) {
            expression.append('×');
        }
    }

    private boolean isValueChar(char c) {
        return Character.isDigit(c) || c == ')' || Character.isLetter(c);
    }

    private void updateDisplay() {
        String text = expression.toString();
        resultLabel.setText(text.isEmpty() ? "0" : text);
    }

    private String formatResult(double result) {
        if (Double.isNaN(result)) return "Error";
        if (Double.isInfinite(result)) return result > 0 ? "∞" : "-∞";
        double rounded = Math.round(result * 1e10) / 1e10;
        if (rounded == Math.floor(rounded) && Math.abs(rounded) < 1e15) return String.valueOf((long) rounded);
        return String.valueOf(rounded);
    }
}