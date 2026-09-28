package com.example.calcfx;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;

import java.util.List;

/** Draws the grid, axes and equation curves onto a Canvas's GraphicsContext. */
public class GraphRenderer {

    private static final Color BACKGROUND = Color.web("#1e1e2e");
    private static final Color GRID_LINE = Color.web("#313244");
    private static final Color AXIS_LINE = Color.web("#6c7086");
    private static final Color AXIS_LABEL = Color.web("#a6adc8");

    public static void draw(GraphicsContext gc, double width, double height,
                            double xMin, double xMax, double yCenter,
                            List<GraphEngine.PlotSeries> series) {
        if (width <= 0 || height <= 0) return;

        double unitsPerPixel = (xMax - xMin) / width;
        double yHalfRange = unitsPerPixel * height / 2.0;
        double yMin = yCenter - yHalfRange;
        double yMax = yCenter + yHalfRange;

        gc.setFill(BACKGROUND);
        gc.fillRect(0, 0, width, height);

        double stepX = niceStep((xMax - xMin) / 10.0);
        double stepY = niceStep((yMax - yMin) / 10.0);

        gc.setFont(Font.font(10));

        gc.setStroke(GRID_LINE);
        gc.setLineWidth(1);
        for (double x = Math.ceil(xMin / stepX) * stepX; x <= xMax; x += stepX) {
            double px = (x - xMin) / unitsPerPixel;
            gc.strokeLine(px, 0, px, height);
        }
        for (double y = Math.ceil(yMin / stepY) * stepY; y <= yMax; y += stepY) {
            double py = height / 2.0 - (y - yCenter) / unitsPerPixel;
            gc.strokeLine(0, py, width, py);
        }

        gc.setStroke(AXIS_LINE);
        gc.setLineWidth(2);
        if (xMin <= 0 && xMax >= 0) {
            double zeroX = (0 - xMin) / unitsPerPixel;
            gc.strokeLine(zeroX, 0, zeroX, height);
        }
        if (yMin <= 0 && yMax >= 0) {
            double zeroY = height / 2.0 - (0 - yCenter) / unitsPerPixel;
            gc.strokeLine(0, zeroY, width, zeroY);
        }

        gc.setFill(AXIS_LABEL);
        double labelY = (yMin <= 0 && yMax >= 0) ? height / 2.0 - (0 - yCenter) / unitsPerPixel + 12 : height - 4;
        labelY = Math.min(Math.max(labelY, 12), height - 4);
        for (double x = Math.ceil(xMin / stepX) * stepX; x <= xMax; x += stepX) {
            if (Math.abs(x) < stepX / 1000.0) continue;
            double px = (x - xMin) / unitsPerPixel;
            gc.fillText(formatTick(x), px + 2, labelY);
        }
        double labelX = (xMin <= 0 && xMax >= 0) ? (0 - xMin) / unitsPerPixel + 4 : 4;
        labelX = Math.min(Math.max(labelX, 4), width - 24);
        for (double y = Math.ceil(yMin / stepY) * stepY; y <= yMax; y += stepY) {
            if (Math.abs(y) < stepY / 1000.0) continue;
            double py = height / 2.0 - (y - yCenter) / unitsPerPixel;
            gc.fillText(formatTick(y), labelX, py - 2);
        }

        // Equation curves — each series is a bag of independent line segments
        // from marching squares, so we just stroke each one directly.
        for (GraphEngine.PlotSeries s : series) {
            gc.setStroke(s.color());
            gc.setLineWidth(2.5);
            for (double[] seg : s.segments()) {
                double px1 = (seg[0] - xMin) / unitsPerPixel;
                double py1 = height / 2.0 - (seg[1] - yCenter) / unitsPerPixel;
                double px2 = (seg[2] - xMin) / unitsPerPixel;
                double py2 = height / 2.0 - (seg[3] - yCenter) / unitsPerPixel;
                gc.strokeLine(px1, py1, px2, py2);
            }
        }
    }

    private static double niceStep(double rawStep) {
        if (rawStep <= 0 || Double.isNaN(rawStep) || Double.isInfinite(rawStep)) return 1;
        double magnitude = Math.pow(10, Math.floor(Math.log10(rawStep)));
        double residual = rawStep / magnitude;
        double niceResidual;
        if (residual < 1.5) niceResidual = 1;
        else if (residual < 3) niceResidual = 2;
        else if (residual < 7) niceResidual = 5;
        else niceResidual = 10;
        return niceResidual * magnitude;
    }

    private static String formatTick(double value) {
        double rounded = Math.round(value * 1000.0) / 1000.0;
        if (rounded == Math.floor(rounded)) return String.valueOf((long) rounded);
        return String.valueOf(rounded);
    }
}