import java.io.IOException;
import java.io.PrintWriter;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

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
    private static final int[] PART_C_III_INPUT_SIZES = {
        10_000, 50_000, 100_000, 500_000
    };

    /**
     * Part (c)(iii) candidates come from {@link ThresholdCandidates}: one
     * threshold per halving interval, which covers every distinct recursion
     * tree. Leaves beyond this size are past the minimum and only get slower.
     */
    private static final int PART_C_III_MAXIMUM_LEAF_SIZE = 2_048;

    /**
     * Part (c)(iii) separates candidates that differ by around one percent, so
     * it needs a tighter measurement than the other parts: more trials, and an
     * execution order that is shuffled and interleaved so that JIT warm-up and
     * CPU thermal drift do not settle on whichever candidate ran first.
     */
    private static final int PART_C_III_TRIALS = 31;
    private static final long EXECUTION_ORDER_SEED = 987_654_321L;

    private static final int PART_D_INPUT_SIZE = 10_000_000;
    private static final int PART_D_OPTIMAL_S = 60;

    private ExperimentRunner() {
    }

    /**
     * Usage: {@code ExperimentRunner [outputDirectory] [parts]}, where parts is
     * a comma separated subset of {@code ci,cii,ciii,d}. Every part is run when
     * it is omitted. Selecting one part avoids rewriting results that a report
     * already quotes.
     */
    public static void main(String[] args) throws IOException {
        Path outputDirectory = args.length == 0
                ? Paths.get("results")
                : Paths.get(args[0]);
        Set<String> parts = args.length < 2
                ? Set.of("ci", "cii", "ciii", "d")
                : new LinkedHashSet<>(Arrays.asList(args[1].split(",")));
        Files.createDirectories(outputDirectory);

        warmUpJvm();
        if (parts.contains("ci")) {
            runPartCI(outputDirectory.resolve("part_i_vary_n.csv"));
        }
        if (parts.contains("cii")) {
            runPartCII(outputDirectory.resolve("part_ii_vary_s.csv"));
        }
        if (parts.contains("ciii")) {
            runPartCIII(
                    outputDirectory.resolve("part_iii_candidates.csv"),
                    outputDirectory.resolve("part_iii_optimal_s.csv"));
        }
        if (parts.contains("d")) {
            runPartD(outputDirectory.resolve("part_d_comparison.csv"));
        }

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
     * Part (c)(iii): determine the threshold that gives the shortest running
     * time, for several input sizes.
     *
     * <p>The candidates are one threshold per halving interval, which covers
     * every distinct recursion tree without re-measuring thresholds that do
     * identical work. One candidate is then measured a second time under a
     * separate label. That control performs exactly the same work as its twin,
     * so the gap between the two is this machine's noise floor, and candidates
     * closer together than that cannot be told apart at all.
     */
    private static void runPartCIII(Path candidatesFile, Path optimalFile)
            throws IOException {
        System.out.println("Running Part (c)(iii): searching for the best S...");

        try (PrintWriter candidateWriter = newWriter(candidatesFile);
                PrintWriter optimalWriter = newWriter(optimalFile)) {
            candidateWriter.println(
                    "n,depth,interval_low,interval_high,s,leaf_size,role,trials,"
                            + "mean_key_comparisons,stddev_key_comparisons,"
                            + "mean_time_ms,median_time_ms,stddev_time_ms");
            optimalWriter.println(
                    "n,candidates,best_s,best_interval_low,best_interval_high,"
                            + "best_leaf_size,best_median_time_ms,tie_band_percent,"
                            + "control_gap_percent,candidates_tied,"
                            + "best_s_by_key_comparisons");

            for (int n : PART_C_III_INPUT_SIZES) {
                List<ThresholdCandidates.Candidate> candidates =
                        ThresholdCandidates.forInputSize(n, PART_C_III_MAXIMUM_LEAF_SIZE);
                int count = candidates.size();
                int controlIndex = count / 2;

                int[] thresholds = new int[count + 1];
                for (int i = 0; i < count; i++) {
                    thresholds[i] = candidates.get(i).threshold();
                }
                thresholds[count] = candidates.get(controlIndex).threshold();

                Metrics[] metrics = measureInterleaved(n, thresholds, PART_C_III_TRIALS);

                for (int i = 0; i < count; i++) {
                    writePartCIIIRow(
                            candidateWriter, n, candidates.get(i), "candidate", metrics[i]);
                }
                writePartCIIIRow(
                        candidateWriter, n, candidates.get(controlIndex), "control",
                        metrics[count]);

                int fastest = 0;
                int fewestComparisons = 0;
                for (int i = 1; i < count; i++) {
                    if (metrics[i].getMedianTimeMs()
                            < metrics[fastest].getMedianTimeMs()) {
                        fastest = i;
                    }
                    if (metrics[i].getMeanKeyComparisons()
                            < metrics[fewestComparisons].getMeanKeyComparisons()) {
                        fewestComparisons = i;
                    }
                }

                // Two standard errors of the fastest candidate's own timings.
                // A single control pair is one draw and can land arbitrarily
                // close by luck, so it is reported as an independent sanity
                // check rather than used to decide ties.
                double bestTime = metrics[fastest].getMedianTimeMs();
                double tieBand = 2.0 * metrics[fastest].getTimeStandardDeviationMs()
                        / Math.sqrt(PART_C_III_TRIALS);
                int tied = 0;
                for (int i = 0; i < count; i++) {
                    if (metrics[i].getMedianTimeMs() <= bestTime + tieBand) {
                        tied++;
                    }
                }

                double twinTime = metrics[controlIndex].getMedianTimeMs();
                double controlGap =
                        Math.abs(metrics[count].getMedianTimeMs() - twinTime)
                                / twinTime * 100.0;

                ThresholdCandidates.Candidate best = candidates.get(fastest);
                optimalWriter.printf(
                        Locale.ROOT,
                        "%d,%d,%d,%d,%d,%d,%.4f,%.2f,%.2f,%d,%d%n",
                        n,
                        count,
                        best.threshold(),
                        best.intervalLow(),
                        best.intervalHigh(),
                        best.leafSize(),
                        bestTime,
                        tieBand / bestTime * 100.0,
                        controlGap,
                        tied,
                        candidates.get(fewestComparisons).threshold());

                System.out.printf(
                        Locale.ROOT,
                        "  n = %,d: %d candidates, fastest S = %d "
                                + "(interval %d..%d, leaf %d), "
                                + "tie band %.2f%%, control gap %.2f%%, %d tied%n",
                        n, count, best.threshold(), best.intervalLow(),
                        best.intervalHigh(), best.leafSize(),
                        tieBand / bestTime * 100.0, controlGap, tied);
            }
        }
    }

    /**
     * Times every threshold once per trial, in a shuffled order, so that drift
     * during the run is shared out evenly instead of favouring whichever
     * threshold would otherwise have been measured first. All thresholds in a
     * trial sort the same array, which makes the comparison paired.
     */
    private static Metrics[] measureInterleaved(int n, int[] thresholds, int trials) {
        int count = thresholds.length;
        long[][] keyComparisonCounts = new long[count][trials];
        long[][] elapsedTimesNs = new long[count][trials];

        List<Integer> executionOrder = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            executionOrder.add(index);
        }
        Random shuffleSource = new Random(EXECUTION_ORDER_SEED + n);

        for (int trial = 0; trial < trials; trial++) {
            Collections.shuffle(executionOrder, shuffleSource);
            long seed = seedFor(n, trial);

            for (int index : executionOrder) {
                int[] input = ArrayGenerator.generateRandomArray(
                        n, MAXIMUM_RANDOM_VALUE, seed);
                HybridMergeSort sorter = new HybridMergeSort();

                long startTime = System.nanoTime();
                sorter.sortArray(input, thresholds[index]);
                elapsedTimesNs[index][trial] = System.nanoTime() - startTime;
                keyComparisonCounts[index][trial] = sorter.getComparisonCount();

                if (!isSorted(input)) {
                    throw new IllegalStateException("Sorting failed for n=" + n
                            + ", S=" + thresholds[index] + ", trial=" + trial);
                }
            }
        }

        Metrics[] metrics = new Metrics[count];
        for (int index = 0; index < count; index++) {
            metrics[index] = Metrics.from(
                    keyComparisonCounts[index], elapsedTimesNs[index]);
        }
        return metrics;
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

    private static void writePartCIIIRow(
            PrintWriter writer,
            int n,
            ThresholdCandidates.Candidate candidate,
            String role,
            Metrics metrics) {
        writer.printf(
                Locale.ROOT,
                "%d,%d,%d,%d,%d,%d,%s,%d,%.2f,%.2f,%.4f,%.4f,%.4f%n",
                n,
                candidate.depth(),
                candidate.intervalLow(),
                candidate.intervalHigh(),
                candidate.threshold(),
                candidate.leafSize(),
                role,
                PART_C_III_TRIALS,
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
