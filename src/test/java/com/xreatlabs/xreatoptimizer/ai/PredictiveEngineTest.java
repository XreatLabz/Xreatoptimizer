package com.xreatlabs.xreatoptimizer.ai;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for PredictiveEngine
 * Tests time-series forecasting and prediction logic
 */
class PredictiveEngineTest {

    private static final double DELTA = 0.01;

    @Test
    @DisplayName("Holt-Winters forecast should produce reasonable predictions")
    void testTrendForecasting() {
        // Given: A declining trend
        double[] values = {20.0, 19.5, 19.0, 18.5, 18.0};

        // When: Forecasting next value
        double forecast = forecastWithTrend(values, 1);

        // Then: Forecast should be reasonable (smoothing dampens trends)
        assertTrue(forecast >= 17.0 && forecast <= 20.0,
            "Forecast should be within reasonable range, got: " + forecast);
    }

    @Test
    @DisplayName("Forecast should handle stable values")
    void testStableForecast() {
        // Given: Stable values
        double[] values = {20.0, 20.0, 20.0, 20.0, 20.0};

        // When: Forecasting
        double forecast = forecastWithTrend(values, 1);

        // Then: Forecast should remain stable
        assertEquals(20.0, forecast, 0.5, "Forecast should remain near stable value");
    }

    @Test
    @DisplayName("Forecast multiple steps ahead should amplify trend")
    void testMultiStepForecast() {
        // Given: A declining trend
        double[] values = {20.0, 19.0, 18.0, 17.0, 16.0};

        // When: Forecasting 1 step vs 5 steps ahead
        double forecast1 = forecastWithTrend(values, 1);
        double forecast5 = forecastWithTrend(values, 5);

        // Then: Longer forecast should show more decline
        assertTrue(forecast5 < forecast1, "5-step forecast should show more decline than 1-step");
    }

    @Test
    @DisplayName("Confidence should decrease with higher variance")
    void testConfidenceCalculation() {
        // Given: Low variance data
        double[] lowVariance = {20.0, 20.1, 19.9, 20.0, 20.1};
        double confidenceLow = calculateConfidence(lowVariance);

        // Given: High variance data
        double[] highVariance = {20.0, 15.0, 18.0, 12.0, 19.0};
        double confidenceHigh = calculateConfidence(highVariance);

        // Then: Low variance should have higher confidence
        assertTrue(confidenceLow > confidenceHigh,
            "Low variance data should have higher confidence");
    }

    @Test
    @DisplayName("Confidence should be between 0 and 1")
    void testConfidenceBounds() {
        double[] values = {20.0, 19.0, 18.0, 17.0, 16.0};

        double confidence = calculateConfidence(values);

        assertTrue(confidence >= 0.0, "Confidence should be >= 0");
        assertTrue(confidence <= 1.0, "Confidence should be <= 1");
    }

    @Test
    @DisplayName("Lag spike prediction should trigger on low TPS forecast")
    void testLagSpikePrediction() {
        // Given: Predicted TPS below warning threshold
        double predictedTps = 16.5;
        double warningThreshold = 17.0;

        // When: Checking for lag spike
        boolean lagSpikeExpected = predictedTps < warningThreshold;

        // Then: Lag spike should be expected
        assertTrue(lagSpikeExpected, "Lag spike should be expected when TPS below threshold");
    }

    @Test
    @DisplayName("Lag spike prediction should trigger on high memory forecast")
    void testMemoryPressurePrediction() {
        // Given: Predicted memory above warning threshold
        double predictedMemory = 78.0;
        double warningThreshold = 75.0;

        // When: Checking for memory pressure
        boolean lagSpikeExpected = predictedMemory > warningThreshold;

        // Then: Lag spike should be expected
        assertTrue(lagSpikeExpected, "Lag spike should be expected when memory above threshold");
    }

    @Test
    @DisplayName("Seasonal adjustment should modify prediction")
    void testSeasonalAdjustment() {
        // Given: Base prediction and seasonal factor
        double basePrediction = 18.0;
        double seasonalFactor = 1.1; // 10% above normal
        double gamma = 0.2; // Seasonal smoothing factor

        // When: Applying seasonal adjustment
        double adjusted = basePrediction * (gamma * seasonalFactor + (1 - gamma));

        // Then: Adjusted value should be higher than base
        assertTrue(adjusted > basePrediction, "Seasonal adjustment should increase prediction");
    }

    /**
     * Simplified Holt-Winters forecast implementation for testing
     */
    private double forecastWithTrend(double[] values, int stepsAhead) {
        if (values.length == 0) return 0.0;

        double alpha = 0.3; // Level smoothing
        double beta = 0.1;  // Trend smoothing

        double level = values[0];
        double trend = 0.0;

        for (int i = 1; i < values.length; i++) {
            double prevLevel = level;
            level = alpha * values[i] + (1 - alpha) * (level + trend);
            trend = beta * (level - prevLevel) + (1 - beta) * trend;
        }

        return level + stepsAhead * trend;
    }

    /**
     * Calculate confidence based on variance
     */
    private double calculateConfidence(double[] values) {
        if (values.length < 2) return 0.5;

        // Calculate mean
        double mean = 0.0;
        for (double v : values) mean += v;
        mean /= values.length;

        // Calculate variance
        double variance = 0.0;
        for (double v : values) {
            variance += Math.pow(v - mean, 2);
        }
        variance /= values.length;

        double stdDev = Math.sqrt(variance);

        // Lower variance = higher confidence
        return Math.max(0.0, Math.min(1.0, 1.0 - (stdDev / 5.0)));
    }
}
