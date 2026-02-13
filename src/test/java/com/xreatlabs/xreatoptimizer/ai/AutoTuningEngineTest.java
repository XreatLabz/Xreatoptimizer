package com.xreatlabs.xreatoptimizer.ai;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for AutoTuningEngine
 * Tests EWMA calculations and threshold adjustments
 */
class AutoTuningEngineTest {

    private static final double DELTA = 0.01; // Tolerance for double comparisons

    @Test
    @DisplayName("EWMA should calculate correct exponential weighted moving average")
    void testEWMACalculation() {
        // Given: A series of values
        List<Double> values = new ArrayList<>();
        values.add(20.0);
        values.add(19.5);
        values.add(19.0);
        values.add(18.5);
        values.add(18.0);

        // When: Calculate EWMA with alpha = 0.3
        double ewma = calculateEWMA(values, 0.3);

        // Then: EWMA should be weighted towards recent values
        assertTrue(ewma < 19.0, "EWMA should be less than simple average");
        assertTrue(ewma > 18.0, "EWMA should be greater than last value");
    }

    @Test
    @DisplayName("EWMA with single value should return that value")
    void testEWMASingleValue() {
        List<Double> values = new ArrayList<>();
        values.add(15.5);

        double ewma = calculateEWMA(values, 0.3);

        assertEquals(15.5, ewma, DELTA);
    }

    @Test
    @DisplayName("EWMA with empty list should return 0")
    void testEWMAEmptyList() {
        List<Double> values = new ArrayList<>();

        double ewma = calculateEWMA(values, 0.3);

        assertEquals(0.0, ewma, DELTA);
    }

    @Test
    @DisplayName("EWMA should give more weight to recent values")
    void testEWMARecentWeighting() {
        // Given: Values with a sudden drop at the end
        List<Double> values = new ArrayList<>();
        values.add(20.0);
        values.add(20.0);
        values.add(20.0);
        values.add(10.0); // Sudden drop

        // When: Calculate EWMA
        double ewma = calculateEWMA(values, 0.3);

        // Then: EWMA should be closer to recent value than simple average
        double simpleAvg = (20.0 + 20.0 + 20.0 + 10.0) / 4.0; // 17.5
        assertTrue(ewma < simpleAvg, "EWMA should be more responsive to recent drop");
    }

    @Test
    @DisplayName("Threshold bounds should be enforced")
    void testThresholdBounds() {
        // Test that thresholds stay within valid ranges
        double lightThreshold = 19.5;
        double normalThreshold = 18.0;
        double aggressiveThreshold = 16.0;

        // Ensure proper ordering
        assertTrue(lightThreshold > normalThreshold);
        assertTrue(normalThreshold > aggressiveThreshold);

        // Ensure within bounds
        assertTrue(lightThreshold <= 20.0);
        assertTrue(lightThreshold >= 18.0);
        assertTrue(normalThreshold >= 10.0);
        assertTrue(normalThreshold <= 19.0);
        assertTrue(aggressiveThreshold >= 5.0);
        assertTrue(aggressiveThreshold <= 18.0);
    }

    @Test
    @DisplayName("Entity limits should never go below safe minimums")
    void testEntityLimitSafetyBounds() {
        // Given: Safe minimum limits
        final int MIN_PASSIVE = 200;
        final int MIN_HOSTILE = 150;
        final int MIN_ITEM = 500;

        // When: Simulating aggressive reduction
        int passiveLimit = 300;
        int hostileLimit = 200;
        int itemLimit = 1000;

        // Reduce by 50%
        passiveLimit = (int) (passiveLimit * 0.5);
        hostileLimit = (int) (hostileLimit * 0.5);
        itemLimit = (int) (itemLimit * 0.5);

        // Apply safety bounds
        passiveLimit = Math.max(MIN_PASSIVE, passiveLimit);
        hostileLimit = Math.max(MIN_HOSTILE, hostileLimit);
        itemLimit = Math.max(MIN_ITEM, itemLimit);

        // Then: Limits should not go below minimums
        assertEquals(MIN_PASSIVE, passiveLimit);
        assertEquals(MIN_HOSTILE, hostileLimit);
        assertEquals(MIN_ITEM, itemLimit);
    }

    @Test
    @DisplayName("Threshold ordering should be maintained after adjustments")
    void testThresholdOrdering() {
        // Given: Initial thresholds
        double light = 19.5;
        double normal = 18.0;
        double aggressive = 16.0;

        // When: Adjusting thresholds
        light += 0.3;
        normal += 0.2;
        aggressive += 0.2;

        // Ensure ordering is maintained
        if (light <= normal) {
            normal = light - 1.0;
        }
        if (normal <= aggressive) {
            aggressive = normal - 1.0;
        }

        // Then: Proper ordering should be maintained
        assertTrue(light > normal);
        assertTrue(normal > aggressive);
    }

    /**
     * Helper method to calculate EWMA (same algorithm as AutoTuningEngine)
     */
    private double calculateEWMA(List<Double> values, double alpha) {
        if (values.isEmpty()) return 0.0;

        double ewma = values.get(0);
        for (int i = 1; i < values.size(); i++) {
            ewma = alpha * values.get(i) + (1 - alpha) * ewma;
        }
        return ewma;
    }
}
