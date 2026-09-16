import java.util.Arrays;

public final class Metrics {
    private final double meanKeyComparisons;
    private final double keyComparisonStandardDeviation;
    private final double meanTimeMs;
    private final double medianTimeMs;
    private final double timeStandardDeviationMs;

    private Metrics(
            double meanKeyComparisons,
            double keyComparisonStandardDeviation,
            double meanTimeMs,
            double medianTimeMs,
            double timeStandardDeviationMs) {
        this.meanKeyComparisons = meanKeyComparisons;
        this.keyComparisonStandardDeviation = keyComparisonStandardDeviation;
        this.meanTimeMs = meanTimeMs;
        this.medianTimeMs = medianTimeMs;
        this.timeStandardDeviationMs = timeStandardDeviationMs;
    }

    public static Metrics from(long[] keyComparisonCounts, long[] timesNs) {
        double meanKeyComparisons = mean(keyComparisonCounts);
        double meanTimeNs = mean(timesNs);

        return new Metrics(
                meanKeyComparisons,
                standardDeviation(keyComparisonCounts, meanKeyComparisons),
                meanTimeNs / 1_000_000.0,
                median(timesNs) / 1_000_000.0,
                standardDeviation(timesNs, meanTimeNs) / 1_000_000.0);
    }

    public double getMeanKeyComparisons() {
        return meanKeyComparisons;
    }

    public double getKeyComparisonStandardDeviation() {
        return keyComparisonStandardDeviation;
    }

    public double getMeanTimeMs() {
        return meanTimeMs;
    }

    public double getMedianTimeMs() {
        return medianTimeMs;
    }

    public double getTimeStandardDeviationMs() {
        return timeStandardDeviationMs;
    }

    private static double mean(long[] values) {
        double sum = 0.0;
        for (long value : values) {
            sum += value;
        }
        return sum / values.length;
    }

    private static double median(long[] values) {
        long[] sortedValues = Arrays.copyOf(values, values.length);
        Arrays.sort(sortedValues);
        int middle = sortedValues.length / 2;

        if (sortedValues.length % 2 == 1) {
            return sortedValues[middle];
        }
        return (sortedValues[middle - 1] + sortedValues[middle]) / 2.0;
    }

    private static double standardDeviation(long[] values, double mean) {
        double squaredDifferenceSum = 0.0;
        for (long value : values) {
            double difference = value - mean;
            squaredDifferenceSum += difference * difference;
        }
        return Math.sqrt(squaredDifferenceSum / values.length);
    }
}
