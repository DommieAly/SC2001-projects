import java.io.IOException;
import java.io.PrintWriter;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

public class ExperimentRunner {
    private static final long BASE_RANDOM_SEED = 20260910L;
    private static final int MAXIMUM_RANDOM_VALUE = 1_000_000;
    private static final int NUMBER_OF_TRIALS = 10;
    private static final ThreadMXBean THREAD_CPU_TIMER =
            ManagementFactory.getThreadMXBean();

    private static final int PART_C_I_FIXED_S = 16;
    private static final int[] PART_C_I_INPUT_SIZES = {
        1_000, 5_000, 10_000, 50_000, 100_000, 500_000,
        1_000_000, 5_000_000, 10_000_000
    };

    private static final int PART_C_II_FIXED_N = 100_000;
    private static final int[] PART_C_II_S_VALUES = createSValues(10, 1_000);
    private static final int[] PART_C_III_S_VALUES = {
        1, 10, 20, 30, 40, 50, 60, 70, 80, 90, 100,
        110, 120, 130, 140, 150, 160, 170, 180, 190, 200,
        300, 500, 1_000
    };

    private static final int[] PART_C_III_INPUT_SIZES = {
        10_000, 50_000, 100_000, 500_000
    };

    private static final int PART_D_INPUT_SIZE = 10_000_000;
    private static final int PART_D_OPTIMAL_S = 60;

    private ExperimentRunner() {
    }

    public static void main(String[] args) throws IOException {
        Path outputDirectory = args.length == 0
                ? Paths.get("results")
                : Paths.get(args[0]);
        Files.createDirectories(outputDirectory);

        warmUpJvm();
        runPartCI(outputDirectory.resolve("part_i_vary_n.csv"));
        runPartCII(outputDirectory.resolve("part_ii_vary_s.csv"));
        runPartCIII(
                outputDirectory.resolve("part_iii_all_candidates.csv"),
                outputDirectory.resolve("part_iii_optimal_s.csv"));
        runPartD(outputDirectory.resolve("part_d_comparison.csv"));

        System.out.println("Experiment complete. Results saved in: "
                + outputDirectory.toAbsolutePath());
    }

    /** Part (c)(i): keep S fixed and measure comparisons for different n. */
    private static void runPartCI(Path outputFile) throws IOException {
        System.out.println("Running Part (c)(i): fixed S, varying n...");

        try (PrintWriter writer = newWriter(outputFile)) {
            writer.println(
                    "n,s,trials,mean_key_comparisons,stddev_key_comparisons,"
                            + "mean_time_ms,median_time_ms,n_log2_n");

            for (int n : PART_C_I_INPUT_SIZES) {
                Metrics metrics = measureHybridMergeSort(n, PART_C_I_FIXED_S);
                writer.printf(
                        Locale.ROOT,
                        "%d,%d,%d,%.2f,%.2f,%.4f,%.4f,%.2f%n",
                        n,
                        PART_C_I_FIXED_S,
                        NUMBER_OF_TRIALS,
                        metrics.getMeanKeyComparisons(),
                        metrics.getKeyComparisonStandardDeviation(),
                        metrics.getMeanTimeMs(),
                        metrics.getMedianTimeMs(),
                        n * log2(n));
            }
        }
    }

    /** Part (c)(ii): keep n fixed and measure comparisons for different S. */
    private static void runPartCII(Path outputFile) throws IOException {
        System.out.println("Running Part (c)(ii): fixed n, varying S...");

        try (PrintWriter writer = newWriter(outputFile)) {
            writer.println(
                    "n,s,trials,mean_key_comparisons,stddev_key_comparisons,"
                            + "mean_time_ms,median_time_ms,merge_growth_proxy,"
                            + "insertion_growth_proxy,total_growth_proxy");

            for (int s : PART_C_II_S_VALUES) {
                Metrics metrics = measureHybridMergeSort(PART_C_II_FIXED_N, s);
                GrowthTerms growth = calculateGrowthTerms(PART_C_II_FIXED_N, s);
                writer.printf(
                        Locale.ROOT,
                        "%d,%d,%d,%.2f,%.2f,%.4f,%.4f,%.2f,%.2f,%.2f%n",
                        PART_C_II_FIXED_N,
                        s,
                        NUMBER_OF_TRIALS,
                        metrics.getMeanKeyComparisons(),
                        metrics.getKeyComparisonStandardDeviation(),
                        metrics.getMeanTimeMs(),
                        metrics.getMedianTimeMs(),
                        growth.mergeTerm,
                        growth.insertionTerm,
                        growth.total());
            }
        }
    }

    /**
     * Part (c)(iii): search all candidate S values for several input sizes.
     * Runtime and comparison-count optima are reported separately.
     */
    private static void runPartCIII(Path allResultsFile, Path optimalResultsFile)
            throws IOException {
        System.out.println("Running Part (c)(iii): searching for the best S...");

        try (PrintWriter allWriter = newWriter(allResultsFile);
                PrintWriter optimalWriter = newWriter(optimalResultsFile)) {
            allWriter.println(
                    "n,s,trials,mean_key_comparisons,stddev_key_comparisons,"
                            + "mean_time_ms,median_time_ms,stddev_time_ms");
            optimalWriter.println(
                    "n,best_s_by_time,median_time_ms,mean_time_ms,"
                            + "best_s_by_key_comparisons,mean_key_comparisons");

            for (int n : PART_C_III_INPUT_SIZES) {
                int bestSByTime = -1;
                int bestSByKeyComparisons = -1;
                Metrics bestTimeMetrics = null;
                Metrics bestKeyComparisonMetrics = null;

                for (int s : PART_C_III_S_VALUES) {
                    Metrics metrics = measureHybridMergeSort(n, s);
                    writePartCIIIResult(allWriter, n, s, metrics);

                    if (bestTimeMetrics == null
                            || metrics.getMedianTimeMs()
                                    < bestTimeMetrics.getMedianTimeMs()) {
                        bestSByTime = s;
                        bestTimeMetrics = metrics;
                    }
                    if (bestKeyComparisonMetrics == null
                            || metrics.getMeanKeyComparisons()
                                    < bestKeyComparisonMetrics.getMeanKeyComparisons()) {
                        bestSByKeyComparisons = s;
                        bestKeyComparisonMetrics = metrics;
                    }
                }

                optimalWriter.printf(
                        Locale.ROOT,
                        "%d,%d,%.4f,%.4f,%d,%.2f%n",
                        n,
                        bestSByTime,
                        bestTimeMetrics.getMedianTimeMs(),
                        bestTimeMetrics.getMeanTimeMs(),
                        bestSByKeyComparisons,
                        bestKeyComparisonMetrics.getMeanKeyComparisons());
            }
        }
    }

    /**
     * Part (d): compare original and hybrid merge sort on 10 million integers.
     * The execution order alternates between trials to reduce ordering bias.
     */
    private static void runPartD(Path outputFile) throws IOException {
        System.out.println(
                "Running Part (d): comparing original and hybrid merge sort...");
        prepareThreadCpuTimer();

        long[] originalKeyComparisonCounts = new long[NUMBER_OF_TRIALS];
        long[] originalCpuTimesNs = new long[NUMBER_OF_TRIALS];
        long[] hybridKeyComparisonCounts = new long[NUMBER_OF_TRIALS];
        long[] hybridCpuTimesNs = new long[NUMBER_OF_TRIALS];

        for (int trial = 0; trial < NUMBER_OF_TRIALS; trial++) {
            long seed = seedFor(PART_D_INPUT_SIZE, trial);

            if (trial % 2 == 0) {
                measureOriginalMergeSortTrial(
                        trial, seed, originalKeyComparisonCounts, originalCpuTimesNs);
                measureHybridMergeSortTrial(
                        trial, seed, hybridKeyComparisonCounts, hybridCpuTimesNs);
            } else {
                measureHybridMergeSortTrial(
                        trial, seed, hybridKeyComparisonCounts, hybridCpuTimesNs);
                measureOriginalMergeSortTrial(
                        trial, seed, originalKeyComparisonCounts, originalCpuTimesNs);
            }
        }

        Metrics originalMetrics =
                Metrics.from(originalKeyComparisonCounts, originalCpuTimesNs);
        Metrics hybridMetrics = Metrics.from(hybridKeyComparisonCounts, hybridCpuTimesNs);

        try (PrintWriter writer = newWriter(outputFile)) {
            writer.println(
                    "algorithm,n,s,trials,mean_key_comparisons,"
                            + "stddev_key_comparisons,mean_cpu_time_ms,"
                            + "median_cpu_time_ms,stddev_cpu_time_ms");
            writePartDResult(writer, "Original Merge Sort", "N/A", originalMetrics);
            writePartDResult(
                    writer,
                    "Hybrid Merge Sort",
                    Integer.toString(PART_D_OPTIMAL_S),
                    hybridMetrics);
        }
    }

    private static Metrics measureHybridMergeSort(int n, int s) {
        long[] keyComparisonCounts = new long[NUMBER_OF_TRIALS];
        long[] elapsedTimesNs = new long[NUMBER_OF_TRIALS];

        for (int trial = 0; trial < NUMBER_OF_TRIALS; trial++) {
            // The same (n, trial) pair always produces the same dataset. This
            // ensures that every S is evaluated on identical input arrays.
            long seed = seedFor(n, trial);
            int[] input = ArrayGenerator.generateRandomArray(
                    n, MAXIMUM_RANDOM_VALUE, seed);
            HybridMergeSort sorter = new HybridMergeSort();

            long startTime = System.nanoTime();
            sorter.sortArray(input, s);
            elapsedTimesNs[trial] = System.nanoTime() - startTime;
            keyComparisonCounts[trial] = sorter.getComparisonCount();

            if (!isSorted(input)) {
                throw new IllegalStateException(
                        "Sorting failed for n=" + n + ", S=" + s + ", trial=" + trial);
            }
        }

        return Metrics.from(keyComparisonCounts, elapsedTimesNs);
    }

    private static void warmUpJvm() {
        final int warmUpRuns = 5;
        final int warmUpSize = 50_000;

        for (int run = 0; run < warmUpRuns; run++) {
            long seed = seedFor(warmUpSize, -run - 1);
            int[] hybridInput = ArrayGenerator.generateRandomArray(
                    warmUpSize, MAXIMUM_RANDOM_VALUE, seed);
            int[] originalInput = ArrayGenerator.generateRandomArray(
                    warmUpSize, MAXIMUM_RANDOM_VALUE, seed);
            new HybridMergeSort().sortArray(hybridInput, PART_C_I_FIXED_S);
            new MergeSort().sortArray(originalInput);
        }
    }

    private static void writePartCIIIResult(
            PrintWriter writer, int n, int s, Metrics metrics) {
        writer.printf(
                Locale.ROOT,
                "%d,%d,%d,%.2f,%.2f,%.4f,%.4f,%.4f%n",
                n,
                s,
                NUMBER_OF_TRIALS,
                metrics.getMeanKeyComparisons(),
                metrics.getKeyComparisonStandardDeviation(),
                metrics.getMeanTimeMs(),
                metrics.getMedianTimeMs(),
                metrics.getTimeStandardDeviationMs());
    }

    private static void measureOriginalMergeSortTrial(
            int trial,
            long seed,
            long[] keyComparisonCounts,
            long[] cpuTimesNs) {
        int[] input = ArrayGenerator.generateRandomArray(
                PART_D_INPUT_SIZE, MAXIMUM_RANDOM_VALUE, seed);
        MergeSort sorter = new MergeSort();

        long startCpuTime = currentThreadCpuTime();
        sorter.sortArray(input);
        cpuTimesNs[trial] = currentThreadCpuTime() - startCpuTime;
        keyComparisonCounts[trial] = sorter.getComparisonCount();

        verifySorted(input, "Original Merge Sort", trial);
    }

    private static void measureHybridMergeSortTrial(
            int trial,
            long seed,
            long[] keyComparisonCounts,
            long[] cpuTimesNs) {
        int[] input = ArrayGenerator.generateRandomArray(
                PART_D_INPUT_SIZE, MAXIMUM_RANDOM_VALUE, seed);
        HybridMergeSort sorter = new HybridMergeSort();

        long startCpuTime = currentThreadCpuTime();
        sorter.sortArray(input, PART_D_OPTIMAL_S);
        cpuTimesNs[trial] = currentThreadCpuTime() - startCpuTime;
        keyComparisonCounts[trial] = sorter.getComparisonCount();

        verifySorted(input, "Hybrid Merge Sort", trial);
    }

    private static void writePartDResult(
            PrintWriter writer, String algorithm, String s, Metrics metrics) {
        writer.printf(
                Locale.ROOT,
                "%s,%d,%s,%d,%.2f,%.2f,%.4f,%.4f,%.4f%n",
                algorithm,
                PART_D_INPUT_SIZE,
                s,
                NUMBER_OF_TRIALS,
                metrics.getMeanKeyComparisons(),
                metrics.getKeyComparisonStandardDeviation(),
                metrics.getMeanTimeMs(),
                metrics.getMedianTimeMs(),
                metrics.getTimeStandardDeviationMs());
    }

    private static void prepareThreadCpuTimer() {
        if (!THREAD_CPU_TIMER.isCurrentThreadCpuTimeSupported()) {
            throw new IllegalStateException("Current-thread CPU timing is not supported.");
        }
        if (!THREAD_CPU_TIMER.isThreadCpuTimeEnabled()) {
            THREAD_CPU_TIMER.setThreadCpuTimeEnabled(true);
        }
    }

    private static long currentThreadCpuTime() {
        return THREAD_CPU_TIMER.getCurrentThreadCpuTime();
    }

    private static void verifySorted(int[] input, String algorithm, int trial) {
        if (!isSorted(input)) {
            throw new IllegalStateException(
                    algorithm + " failed to sort the input in trial " + trial);
        }
    }

    private static GrowthTerms calculateGrowthTerms(int n, int s) {
        int effectiveS = Math.min(n, s);
        double mergeTerm = n * log2((double) n / effectiveS);
        double insertionTerm = (double) n * effectiveS;
        return new GrowthTerms(mergeTerm, insertionTerm);
    }

    private static long seedFor(int n, int trial) {
        return BASE_RANDOM_SEED + 1_000_003L * n + trial;
    }

    private static int[] createSValues(int step, int maximum) {
        int[] values = new int[maximum / step + 1];
        values[0] = 1;
        for (int i = 1; i < values.length; i++) {
            values[i] = i * step;
        }
        return values;
    }

    private static double log2(double value) {
        return Math.log(value) / Math.log(2.0);
    }

    private static boolean isSorted(int[] values) {
        for (int i = 1; i < values.length; i++) {
            if (values[i - 1] > values[i]) {
                return false;
            }
        }
        return true;
    }

    private static PrintWriter newWriter(Path outputFile) throws IOException {
        return new PrintWriter(Files.newBufferedWriter(
                outputFile, StandardCharsets.UTF_8));
    }

    private static final class GrowthTerms {
        private final double mergeTerm;
        private final double insertionTerm;

        private GrowthTerms(double mergeTerm, double insertionTerm) {
            this.mergeTerm = mergeTerm;
            this.insertionTerm = insertionTerm;
        }

        private double total() {
            return mergeTerm + insertionTerm;
        }
    }
}
