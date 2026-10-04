import java.util.Arrays;

/** Summary of repeated runs of one algorithm on one graph configuration. */
public final class Metrics {
    private final int trials;
    private final double meanQueueComparisons;
    private final double meanRelaxations;
    private final double meanDecreaseKeys;
    private final double meanEntriesScanned;
    private final double meanCpuTimeMs;
    private final double medianCpuTimeMs;
    private final double cpuTimeStandardDeviationMs;

    private Metrics(
            int trials,
            double meanQueueComparisons,
            double meanRelaxations,
            double meanDecreaseKeys,
            double meanEntriesScanned,
            double meanCpuTimeMs,
            double medianCpuTimeMs,
            double cpuTimeStandardDeviationMs) {
        this.trials = trials;
        this.meanQueueComparisons = meanQueueComparisons;
        this.meanRelaxations = meanRelaxations;
        this.meanDecreaseKeys = meanDecreaseKeys;
        this.meanEntriesScanned = meanEntriesScanned;
        this.meanCpuTimeMs = meanCpuTimeMs;
        this.medianCpuTimeMs = medianCpuTimeMs;
        this.cpuTimeStandardDeviationMs = cpuTimeStandardDeviationMs;
    }

    /** Every array holds one value per trial; only the first {@code trials} entries are used. */
    public static Metrics from(
            int trials,
            long[] queueComparisonCounts,
            long[] relaxationCounts,
            long[] decreaseKeyCounts,
            long[] entriesScannedCounts,
            long[] cpuTimesNs) {
        long[] times = Arrays.copyOf(cpuTimesNs, trials);
        double meanTimeNs = mean(times);

        return new Metrics(
                trials,
                mean(Arrays.copyOf(queueComparisonCounts, trials)),
                mean(Arrays.copyOf(relaxationCounts, trials)),
                mean(Arrays.copyOf(decreaseKeyCounts, trials)),
                mean(Arrays.copyOf(entriesScannedCounts, trials)),
                meanTimeNs / 1_000_000.0,
                median(times) / 1_000_000.0,
                standardDeviation(times, meanTimeNs) / 1_000_000.0);
    }

    public int getTrials() {
        return trials;
    }

    public double getMeanQueueComparisons() {
        return meanQueueComparisons;
    }

    public double getMeanRelaxations() {
        return meanRelaxations;
    }

    public double getMeanDecreaseKeys() {
        return meanDecreaseKeys;
    }

    public double getMeanEntriesScanned() {
        return meanEntriesScanned;
    }

    public double getMeanCpuTimeMs() {
        return meanCpuTimeMs;
    }

    public double getMedianCpuTimeMs() {
        return medianCpuTimeMs;
    }

    public double getCpuTimeStandardDeviationMs() {
        return cpuTimeStandardDeviationMs;
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
