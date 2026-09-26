package com.example.calcfx;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

public class HelloController {

    private enum ViewMode { CALCULATOR, CURRENCY, HISTORY, GRAPH }

    @FXML private Label statusLabel;
    @FXML private Label expressionLabel;
    @FXML private Label resultLabel;
    @FXML private Button angleModeButton;

    // Currency converter controls
    @FXML private Button currencyToggle;
    @FXML private GridPane calculatorGrid;
    @FXML private VBox currencyPane;
    @FXML private TextField amountField;
    @FXML private ComboBox<String> fromCurrency;
    @FXML private ComboBox<String> toCurrency;
    @FXML private Label conversionResultLabel;
    @FXML private Label rateInfoLabel;

    // History controls
    @FXML private Button historyToggle;
    @FXML private VBox historyPane;
    @FXML private ListView<DatabaseManager.HistoryEntry> historyListView;

    // Graph controls
    @FXML private Button graphToggle;
    @FXML private VBox graphPane;
    @FXML private VBox functionListBox;
    @FXML private Pane graphCanvasContainer;
    @FXML private Canvas graphCanvas;

    private StringBuilder expression = new StringBuilder();
    private boolean justCalculated = false;
    private int openParens = 0;
    private double memory = 0;
    private CalculatorEngine.AngleMode angleMode = CalculatorEngine.AngleMode.DEGREES;

    private final CurrencyService currencyService = new CurrencyService();
    private Map<String, Double> rates;

    private final DatabaseManager db = DatabaseManager.getInstance();
    private ViewMode currentView = ViewMode.CALCULATOR;

    // ---------- Graphing state ----------

    private static class FunctionRow {
        TextField field;
        Color color;
        HBox container;
    }

    private static final Color[] FUNCTION_COLORS = {
            Color.web("#f38ba8"), Color.web("#a6e3a1"), Color.web("#89b4fa"),
            Color.web("#fab387"), Color.web("#cba6f7"), Color.web("#94e2d5")
    };
    private int nextColorIndex = 0;

    private final List<FunctionRow> functionRows = new ArrayList<>();

    private double graphXMin = -10, graphXMax = 10, graphYCenter = 0;
    private double lastMouseX, lastMouseY;

    // A single background thread does all graph math (parsing + sampling),
    // so dragging/zooming/typing never blocks the JavaFX Application Thread.
    private final ExecutorService graphExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "graph-compute");
        t.setDaemon(true);
        return t;
    });
    // Every redraw request gets a stamp. If a newer request arrives before an
    // older one finishes, the older one's result is discarded instead of being
    // drawn — this stops fast dragging/typing from showing stale frames out of order.
    private final AtomicLong graphRequestSeq = new AtomicLong(0);
    private PauseTransition graphDebounce;

    @FXML
    public void initialize() {
        updateStatus();
        resultLabel.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) newScene.setOnKeyPressed(this::handleKeyPress);
        });

        amountField.textProperty().addListener((obs, old, text) -> {
            if (!text.matches("\\d*(\\.\\d*)?")) {
                amountField.setText(old);
            } else {
                performConversion(false);
            }
        });
        fromCurrency.valueProperty().addListener((obs, o, n) -> performConversion(false));
        toCurrency.valueProperty().addListener((obs, o, n) -> performConversion(false));

        setupHistoryList();
        setupGraph();
    }

    // ---------- View switching ----------

    @FXML protected void onToggleCurrency() { setView(ViewMode.CURRENCY); }
    @FXML protected void onToggleHistory() { setView(ViewMode.HISTORY); }
    @FXML protected void onToggleGraph() { setView(ViewMode.GRAPH); }

    private void setView(ViewMode requested) {
        ViewMode previous = currentView;
        currentView = (currentView == requested) ? ViewMode.CALCULATOR : requested;

        boolean calc = currentView == ViewMode.CALCULATOR;
        boolean curr = currentView == ViewMode.CURRENCY;
        boolean hist = currentView == ViewMode.HISTORY;
        boolean graph = currentView == ViewMode.GRAPH;

        calculatorGrid.setVisible(calc);
        calculatorGrid.setManaged(calc);
        currencyPane.setVisible(curr);
        currencyPane.setManaged(curr);
        historyPane.setVisible(hist);
        historyPane.setManaged(hist);
        graphPane.setVisible(graph);
        graphPane.setManaged(graph);

        currencyToggle.setText(curr ? "🔢" : "💱");
        historyToggle.setText(hist ? "🔢" : "🕘");
        graphToggle.setText(graph ? "🔢" : "📈");

        resizeWindowForGraphView(previous, currentView);

        if (curr && rates == null) loadRates();
        if (hist) refreshHistory();
        if (graph) requestGraphRedraw();
    }

    /** The graph needs real width beside the calculator, so grow/shrink the window when toggled. */
    private void resizeWindowForGraphView(ViewMode previous, ViewMode now) {
        if (resultLabel.getScene() == null) return;
        Stage stage = (Stage) resultLabel.getScene().getWindow();
        boolean wasGraph = previous == ViewMode.GRAPH;
        boolean isGraph = now == ViewMode.GRAPH;
        if (isGraph && !wasGraph) {
            stage.setWidth(stage.getWidth() + 420);
        } else if (!isGraph && wasGraph) {
            stage.setWidth(Math.max(400, stage.getWidth() - 420));
        }
    }

    // ---------- Currency converter ----------

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

    @FXML protected void onSwapCurrencies() {
        String from = fromCurrency.getValue();
        fromCurrency.setValue(toCurrency.getValue());
        toCurrency.setValue(from);
    }

    @FXML protected void onConvert() {
        performConversion(true);
    }

    private void performConversion(boolean saveToHistory) {
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

        double amount = Double.parseDouble(text);
        double result = amount / rates.get(from) * rates.get(to);
        String amountStr = formatResult(amount);
        String resultStr = formatResult(result);

        conversionResultLabel.setText(amountStr + " " + from + " = " + resultStr + " " + to);
        rateInfoLabel.setText("1 " + from + " = " + formatResult(rates.get(to) / rates.get(from)) + " " + to);

        if (saveToHistory) {
            db.saveConversion(amountStr + " " + from + " → " + to, resultStr + " " + to);
        }
    }

    // ---------- History panel ----------

    private void setupHistoryList() {
        historyListView.setPlaceholder(new Label("No history yet"));
        historyListView.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(DatabaseManager.HistoryEntry entry, boolean empty) {
                super.updateItem(entry, empty);
                if (empty || entry == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    String icon = entry.type().equals("CONVERT") ? "💱" : "🧮";
                    setText(icon + "  " + entry.expression() + "  =  " + entry.result()
                            + "\n" + entry.timestamp());
                }
            }
        });
        historyListView.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                DatabaseManager.HistoryEntry selected = historyListView.getSelectionModel().getSelectedItem();
                if (selected != null && selected.type().equals("CALC")) {
                    setView(ViewMode.CALCULATOR);
                    expression = new StringBuilder(selected.result());
                    justCalculated = true;
                    updateDisplay();
                }
            }
        });
    }

    private void refreshHistory() {
        historyListView.getItems().setAll(db.getRecentHistory(100));
    }

    @FXML protected void onClearHistory() {
        db.clearHistory();
        refreshHistory();
    }

    // ---------- Graphing ----------

    private void setupGraph() {
        graphCanvas.widthProperty().bind(graphCanvasContainer.widthProperty());
        graphCanvas.heightProperty().bind(graphCanvasContainer.heightProperty());
        graphCanvas.widthProperty().addListener((obs, o, n) -> requestGraphRedraw());
        graphCanvas.heightProperty().addListener((obs, o, n) -> requestGraphRedraw());

        graphDebounce = new PauseTransition(Duration.millis(200));
        graphDebounce.setOnFinished(e -> requestGraphRedraw());

        addFunctionRow(); // start with one empty row

        graphCanvas.setOnMousePressed(e -> {
            lastMouseX = e.getX();
            lastMouseY = e.getY();
        });
        graphCanvas.setOnMouseDragged(e -> {
            double unitsPerPixel = (graphXMax - graphXMin) / graphCanvas.getWidth();
            double dxPixels = e.getX() - lastMouseX;
            double dyPixels = e.getY() - lastMouseY;
            graphXMin -= dxPixels * unitsPerPixel;
            graphXMax -= dxPixels * unitsPerPixel;
            graphYCenter -= dyPixels * unitsPerPixel;
            lastMouseX = e.getX();
            lastMouseY = e.getY();
            requestGraphRedraw();
        });
        graphCanvas.setOnScroll(e -> {
            double zoomFactor = e.getDeltaY() > 0 ? 0.9 : 1.1; // scroll up = zoom in
            double mouseWorldX = graphXMin + (e.getX() / graphCanvas.getWidth()) * (graphXMax - graphXMin);
            graphXMin = mouseWorldX - (mouseWorldX - graphXMin) * zoomFactor;
            graphXMax = mouseWorldX + (graphXMax - mouseWorldX) * zoomFactor;
            requestGraphRedraw();
            e.consume();
        });
    }

    @FXML protected void onAddFunction() {
        addFunctionRow();
    }

    @FXML protected void onResetGraphView() {
        graphXMin = -10;
        graphXMax = 10;
        graphYCenter = 0;
        requestGraphRedraw();
    }

    private void addFunctionRow() {
        FunctionRow row = new FunctionRow();
        row.color = FUNCTION_COLORS[nextColorIndex % FUNCTION_COLORS.length];
        nextColorIndex++;

        Region swatch = new Region();
        swatch.setPrefSize(14, 14);
        swatch.setStyle("-fx-background-color: " + toHex(row.color) + "; -fx-background-radius: 4;");

        Label yEquals = new Label("y =");
        yEquals.setStyle("-fx-text-fill: #cdd6f4; -fx-font-size: 13px;");

        TextField field = new TextField();
        field.setPromptText("e.g. sin(x)");
        field.getStyleClass().add("function-field");
        HBox.setHgrow(field, Priority.ALWAYS);
        field.textProperty().addListener((obs, o, n) -> scheduleGraphRedraw());
        row.field = field;

        Button removeBtn = new Button("✕");
        removeBtn.getStyleClass().add("function-remove-btn");
        removeBtn.setOnAction(e -> {
            functionRows.remove(row);
            functionListBox.getChildren().remove(row.container);
            requestGraphRedraw();
        });

        HBox container = new HBox(8, swatch, yEquals, field, removeBtn);
        container.setAlignment(Pos.CENTER_LEFT);
        row.container = container;

        functionRows.add(row);
        functionListBox.getChildren().add(container);
    }

    private String toHex(Color c) {
        return String.format("#%02X%02X%02X",
                (int) Math.round(c.getRed() * 255),
                (int) Math.round(c.getGreen() * 255),
                (int) Math.round(c.getBlue() * 255));
    }

    private void scheduleGraphRedraw() {
        graphDebounce.stop();
        graphDebounce.playFromStart();
    }

    /**
     * Kicks off a background computation of every function's sample points, then
     * hands the finished result back to the JavaFX Application Thread to draw.
     * See the class-level comment on graphExecutor/graphRequestSeq for why.
     */
    private void requestGraphRedraw() {
        if (graphCanvas.getWidth() <= 0 || graphCanvas.getHeight() <= 0) return;

        long myRequestId = graphRequestSeq.incrementAndGet();

        // Snapshot everything the background thread needs right now — it must
        // never touch live JavaFX controls itself, only these plain copies.
        List<String> expressions = new ArrayList<>();
        List<Color> colors = new ArrayList<>();
        for (FunctionRow row : functionRows) {
            String text = row.field.getText().trim();
            if (!text.isEmpty()) {
                expressions.add(text);
                colors.add(row.color);
            }
        }
        double xMin = graphXMin, xMax = graphXMax, yCenter = graphYCenter;
        double width = graphCanvas.getWidth(), height = graphCanvas.getHeight();
        int samples = Math.max(2, (int) width);

        graphExecutor.submit(() -> {
            if (myRequestId != graphRequestSeq.get()) return; // already superseded, skip

            List<GraphEngine.PlotSeries> series = new ArrayList<>();
            for (int i = 0; i < expressions.size(); i++) {
                try {
                    GraphEngine.ParsedFunction fn = GraphEngine.parse(expressions.get(i));
                    double[] ys = GraphEngine.sample(fn, xMin, xMax, samples);
                    series.add(new GraphEngine.PlotSeries(colors.get(i), ys));
                } catch (Exception ignored) {
                    // invalid/incomplete expression while typing — just skip it
                }
            }

            if (myRequestId != graphRequestSeq.get()) return; // superseded mid-computation

            Platform.runLater(() -> {
                if (myRequestId != graphRequestSeq.get()) return; // double-check on the FX thread
                GraphRenderer.draw(graphCanvas.getGraphicsContext2D(), width, height, xMin, xMax, yCenter, series);
            });
        });
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
        String original = expression.toString();
        try {
            double result = CalculatorEngine.evaluate(original, angleMode);
            String formatted = formatResult(result);
            expressionLabel.setText(original + " =");
            resultLabel.setText(formatted);
            expression = new StringBuilder(formatted);
            openParens = 0;
            justCalculated = true;
            db.saveCalculation(original, formatted);
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
        if (currentView != ViewMode.CALCULATOR) return;
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