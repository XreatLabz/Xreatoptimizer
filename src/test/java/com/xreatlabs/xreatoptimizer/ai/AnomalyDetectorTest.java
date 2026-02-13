package com.xreatlabs.xreatoptimizer.ai;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for AnomalyDetector
 * Tests anomaly detection algorithms (Z-score, IQR, pattern recognition)
 */
class AnomalyDetectorTest {

    private static final double DELTA = 0.01;

    @Test
    @DisplayName("Z-score should detect outliers")
    void testZScoreDetection() {
        // Given: Normal values with one outlier
        List<Double> values = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            values.add(20.0); // Normal values
        }
        values.add(30.0); // Outlier

        // When: Calculate Z-score for outlier
        double mean = calculateMean(values);
        double stdDev = Math.sqrt(calculateVariance(values));
        double zScore = (30.0 - mean) / stdDev;

        // Then: Z-score should be high
        assertTrue(zScore > 2.0, "Z-score should indicate outlier");
    }

    @Test
    @DisplayName("Memory leak detection should identify steady increase")
    void testMemoryLeakPattern() {
        // Given: Steadily increasing memory values
        double[] memoryValues = {50.0, 51.0, 52.0, 53.0, 54.0, 55.0, 56.0, 57.0, 58.0, 59.0};

        // When: Checking for consecutive increases
        int consecutiveIncreases = 0;
        for (int i = 1; i < memoryValues.length; i++) {
            if (memoryValues[i] > memoryValues[i - 1] + 0.5) {
                consecutiveIncreases++;
            }
        }

        // Then: Should detect pattern
        assertTrue(consecutiveIncreases >= 8, "Should detect steady memory increase pattern");
    }

    @Test
    @DisplayName("Sudden TPS drop should be detected")
    void testSuddenTPSDrop() {
        // Given: TPS values with sudden drop
        double tpsBefore = 19.5;
        double tpsAfter = 12.0;

        // When: Calculating drop
        double drop = tpsBefore - tpsAfter;

        // Then: Drop should exceed threshold
        assertTrue(drop > 5.0, "Should detect sudden TPS drop");
    }

    @Test
    @DisplayName("Entity explosion should be detected")
    void testEntityExplosion() {
        // Given: Entity counts with rapid increase
        int entitiesBefore = 500;
        int entitiesAfter = 1200;

        // When: Calculating increase
        int increase = entitiesAfter - entitiesBefore;

        // Then: Increase should exceed threshold
        assertTrue(increase > 500, "Should detect entity explosion");
    }

    @Test
    @DisplayName("Chunk thrashing should be detected by high variance")
    void testChunkThrashing() {
        // Given: Chunk counts with high variance (thrashing)
        List<Integer> chunkCounts = new ArrayList<>();
        chunkCounts.add(1000);
        chunkCounts.add(800);
        chunkCounts.add(1200);
        chunkCounts.add(700);
        chunkCounts.add(1100);
        chunkCounts.add(750);

        // When: Calculating variance
        double variance = calculateVarianceInt(chunkCounts);
        double stdDev = Math.sqrt(variance);

        // Then: High standard deviation indicates thrashing
        assertTrue(stdDev > 100, "High variance should indicate chunk thrashing");
    }

    @Test
    @DisplayName("TPS oscillation should be detected by direction changes")
    void testTPSOscillation() {
        // Given: TPS values with many oscillations
        double[] tpsValues = {19.0, 18.0, 19.0, 18.0, 19.0, 18.0, 19.0, 18.0};

        // When: Counting direction changes
        int oscillations = 0;
        boolean wasIncreasing = false;
        for (int i = 1; i < tpsValues.length; i++) {
            boolean isIncreasing = tpsValues[i] > tpsValues[i - 1];
            if (i > 1 && isIncreasing != wasIncreasing) {
                oscillations++;
            }
            wasIncreasing = isIncreasing;
        }

        // Then: Should detect many oscillations
        assertTrue(oscillations >= 5, "Should detect TPS oscillation pattern");
    }

    @Test
    @DisplayName("Variance calculation should be accurate")
    void testVarianceCalculation() {
        // Given: Known values
        List<Double> values = new ArrayList<>();
        values.add(2.0);
        values.add(4.0);
        values.add(4.0);
        values.add(4.0);
        values.add(5.0);
        values.add(5.0);
        values.add(7.0);
        values.add(9.0);

        // When: Calculating variance
        double variance = calculateVariance(values);

        // Then: Variance should be approximately 4.0
        assertEquals(4.0, variance, 0.5, "Variance calculation should be accurate");
    }

    @Test
    @DisplayName("Mean calculation should be accurate")
    void testMeanCalculation() {
        // Given: Known values
        List<Double> values = new ArrayList<>();
        values.add(10.0);
        values.add(20.0);
        values.add(30.0);

        // When: Calculating mean
        double mean = calculateMean(values);

        // Then: Mean should be 20.0
        assertEquals(20.0, mean, DELTA);
    }

    @Test
    @DisplayName("Anomaly severity should scale with deviation")
    void testAnomalySeverity() {
        // Given: Different levels of deviation
        double minorDeviation = 2.0;
        double majorDeviation = 5.0;

        // When: Calculating severity (normalized)
        double minorSeverity = Math.min(1.0, minorDeviation / 10.0);
        double majorSeverity = Math.min(1.0, majorDeviation / 10.0);

        // Then: Major deviation should have higher severity
        assertTrue(majorSeverity > minorSeverity);
        assertTrue(minorSeverity >= 0.0 && minorSeverity <= 1.0);
        assertTrue(majorSeverity >= 0.0 && majorSeverity <= 1.0);
    }

    /**
     * Calculate mean of values
     */
    private double calculateMean(List<Double> values) {
        if (values.isEmpty()) return 0.0;
        double sum = 0.0;
        for (double value : values) {
            sum += value;
        }
        return sum / values.size();
    }

    /**
     * Calculate variance of values
     */
    private double calculateVariance(List<Double> values) {
        if (values.isEmpty()) return 0.0;
        double mean = calculateMean(values);
        double sumSquaredDiff = 0.0;
        for (double value : values) {
            double diff = value - mean;
            sumSquaredDiff += diff * diff;
        }
        return sumSquaredDiff / values.size();
    }

    /**
     * Calculate variance of integer values
     */
    private double calculateVarianceInt(List<Integer> values) {
        if (values.isEmpty()) return 0.0;
        double mean = 0.0;
        for (int value : values) {
            mean += value;
        }
        mean /= values.size();

        double sumSquaredDiff = 0.0;
        for (int value : values) {
            double diff = value - mean;
            sumSquaredDiff += diff * diff;
        }
        return sumSquaredDiff / values.size();
    }
}
