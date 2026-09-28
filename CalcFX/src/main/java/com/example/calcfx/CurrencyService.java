package com.example.calcfx;

import javafx.application.Platform;
import org.json.JSONObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;


public class CurrencyService {

    private static final String API_URL = "https://open.er-api.com/v6/latest/USD";

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();


    public void fetchRates(Consumer<Map<String, Double>> onSuccess, Consumer<String> onError) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();

        client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenAccept(response -> {
                    try {
                        if (response.statusCode() != 200) {
                            throw new RuntimeException("HTTP " + response.statusCode());
                        }
                        JSONObject json = new JSONObject(response.body());
                        if (!"success".equals(json.optString("result"))) {
                            throw new RuntimeException("API returned an error");
                        }
                        JSONObject ratesJson = json.getJSONObject("rates");
                        Map<String, Double> rates = new TreeMap<>();
                        for (String code : ratesJson.keySet()) {
                            rates.put(code, ratesJson.getDouble(code));
                        }
                        Platform.runLater(() -> onSuccess.accept(rates));
                    } catch (Exception e) {
                        String msg = e.getMessage();
                        Platform.runLater(() -> onError.accept(msg));
                    }
                })
                .exceptionally(ex -> {
                    Platform.runLater(() -> onError.accept("No internet connection"));
                    return null;
                });
    }
}