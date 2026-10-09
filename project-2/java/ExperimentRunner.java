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
    private static final long BASE_RANDOM_SEED = 20261004L;
    private static final int MAXIMUM_WEIGHT = 1_000;
    private static final int SOURCE_VERTEX = 0;
    private static final ThreadMXBean THREAD_CPU_TIMER =
            ManagementFactory.getThreadMXBean();

    /**
     * Each trial of a configuration uses a new graph, seeded by (V, E, trial),
     * so no implementation is timed twice in a row on the same graph.
     * Repeating one graph would let the CPU's branch predictor learn the run
     * and make short runs look faster. Small graphs instead get more trials:
     * trials continue past MINIMUM_TRIALS until MINIMUM_MEASURED_NS of CPU
     * time has been measured, up to MAXIMUM_TRIALS.
     */
    private static final int MINIMUM_TRIALS = 10;
    private static final int MAXIMUM_TRIALS = 100;
    private static final long MINIMUM_MEASURED_NS = 100_000_000L;

    /**
     * Parts (a) and (b) vary |E| at this |V|, from a single cycle to a complete
     * graph, with every weighting: uniform (best case), random (average case)
     * and worst case.
     */
    private static final int FIXED_VERTEX_COUNT = 5_000;
    private static final int[] FIXED_V_OUT_DEGREES = {
        1, 2, 5, 10, 20, 50, 100, 200, 500, 1_000, 2_000,
        2_500, 3_000, 3_500, 4_000, 4_500, 4_999
    };

    /**
     * Parts (a) and (b) vary |V| with this many edges, so that only |V|
     * changes. It must lie between V, for the Hamiltonian cycle, and V(V-1)
     * for every V varied: at V = 500 a complete graph has 249,500 edges.
     */
    private static final long FIXED_EDGE_COUNT = 100_000;

    /**
     * The matrix needs 4V^2 bytes (256 MB at V = 8,000), so part (a) stays in
     * the thousands to keep the run short. V = 100,000 would need 40 GB for
     * the matrix.
     */
    private static final int[] PART_A_VERTEX_COUNTS = {
        500, 1_000, 2_000, 3_000, 4_000, 5_000, 6_000, 7_000, 8_000
    };

    /**
     * The lists need only Theta(V + E) memory, so part (b) continues past part
     * (a) up to V = E, where the graph is the Hamiltonian cycle alone. Up to
     * 8,000 the values are those of part (a), so the first trials of each
     * share their graphs.
     */
    private static final int[] PART_B_VERTEX_COUNTS = {
        500, 1_000, 2_000, 3_000, 4_000, 5_000, 6_000, 7_000, 8_000,
        10_000, 20_000, 50_000, 100_000
    };

    /** The branch control of part (a) compares two ways of writing the edge test at this |V|. */
    private static final int CONTROL_VERTEX_COUNT = 4_000;
    private static final double[] CONTROL_DENSITIES = {
        0.001, 0.05, 0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9, 0.95, 1.0
    };

    /**
     * Part (c) runs every implementation on every graph of this grid, with
     * random and worst-case weights only. The weightings are listed by name
     * so that adding a weighting does not change which part (c)
     * configurations are measured.
     */
    private static final Weights[] PART_C_WEIGHTS = {Weights.RANDOM, Weights.WORST_CASE};
    private static final int[] PART_C_VERTEX_COUNTS = {1_000, 2_000, 4_000, 8_000};
    private static final double[] PART_C_DENSITIES = {
        0.001, 0.002, 0.005, 0.01, 0.02, 0.05, 0.1, 0.2, 0.35, 0.5,
        0.65, 0.8, 0.9, 0.95, 0.98, 1.0
    };
    private static final long EXECUTION_ORDER_SEED = 987_654_321L;

    private ExperimentRunner() {
    }

    /**
     * The three weightings of the same random edges; see {@link GraphGenerator}.
     * Uniform weights give the fewest decrease-keys (best case), random
     * weights the average case, and worst-case weights one per forward edge.
     */
    private enum Weights {
        UNIFORM("uniform"),
        RANDOM("random"),
        WORST_CASE("worst_case");

        private final String label;

        Weights(String label) {
            this.label = label;
        }

        private AdjacencyListGraph build(int v, long e, long seed) {
            return switch (this) {
                case UNIFORM -> GraphGenerator.uniformGraph(v, e, seed);
                case RANDOM -> GraphGenerator.randomGraph(v, e, MAXIMUM_WEIGHT, seed);
                case WORST_CASE -> GraphGenerator.worstCaseGraph(v, e, seed);
            };
        }
    }

    /** The four combinations of graph representation and priority queue. */
    private enum Variant {
        MATRIX_ARRAY("Matrix + array (a)", true),
        LIST_HEAP("Lists + heap (b)", false),
        MATRIX_HEAP("Matrix + heap", true),
        LIST_ARRAY("Lists + array", false);

        private final String label;
        private final boolean usesMatrix;

        Variant(String label, boolean usesMatrix) {
            this.label = label;
            this.usesMatrix = usesMatrix;
        }
    }

    /** The result, operation counts and CPU time of one run. */
    private record Execution(
            ShortestPaths paths,
            long queueComparisons,
            long relaxations,
            long decreaseKeys,
            long entriesScanned,
            long cpuTimeNs) {
    }

    /** Per-trial values for one implementation on one configuration. */
    private static final class TrialLog {
        private final long[] queueComparisons = new long[MAXIMUM_TRIALS];
        private final long[] relaxations = new long[MAXIMUM_TRIALS];
        private final long[] decreaseKeys = new long[MAXIMUM_TRIALS];
        private final long[] entriesScanned = new long[MAXIMUM_TRIALS];
        private final long[] cpuTimesNs = new long[MAXIMUM_TRIALS];

        private void record(int trial, Execution execution) {
            queueComparisons[trial] = execution.queueComparisons();
            relaxations[trial] = execution.relaxations();
            decreaseKeys[trial] = execution.decreaseKeys();
            entriesScanned[trial] = execution.entriesScanned();
            cpuTimesNs[trial] = execution.cpuTimeNs();
        }

        private Metrics summarise(int trials) {
            return Metrics.from(
                    trials, queueComparisons, relaxations, decreaseKeys,
                    entriesScanned, cpuTimesNs);
        }
    }

    /**
     * Usage: {@code ExperimentRunner [outputDirectory] [parts]}, where parts is
     * a comma separated subset of {@code a,b,c}. Every part is run when it is
     * omitted. Selecting one part avoids rewriting results that a report
     * already quotes.
     */
    public static void main(String[] args) throws IOException {
        Path outputDirectory = args.length == 0
                ? Paths.get("results")
                : Paths.get(args[0]);
        Set<String> parts = args.length < 2
                ? Set.of("a", "b", "c")
                : new LinkedHashSet<>(Arrays.asList(args[1].split(",")));
        Files.createDirectories(outputDirectory);
        prepareThreadCpuTimer();

        warmUpJvm();
        if (parts.contains("a")) {
            runPartA(
                    outputDirectory.resolve("part_a_vary_v.csv"),
                    outputDirectory.resolve("part_a_vary_e.csv"),
                    outputDirectory.resolve("part_a_branch_control.csv"));
        }
        if (parts.contains("b")) {
            runPartB(
                    outputDirectory.resolve("part_b_vary_v.csv"),
                    outputDirectory.resolve("part_b_vary_e.csv"));
        }
        if (parts.contains("c")) {
            runPartC(
                    outputDirectory.resolve("part_c_comparison.csv"),
                    outputDirectory.resolve("part_c_crossover.csv"));
        }

        System.out.println("Experiment complete. Results saved in: "
                + outputDirectory.toAbsolutePath());
    }

    /**
     * Part (a): adjacency matrix and array priority queue. (i) |V| is varied
     * at a fixed |E| with random weights; (ii) and (iii) vary |E| at a fixed
     * |V| with every weighting; then the branch control is run.
     */
    private static void runPartA(Path varyVFile, Path varyEFile, Path controlFile)
            throws IOException {
        System.out.println("Running Part (a): adjacency matrix + array queue...");
        writeFixedESweep(varyVFile, Variant.MATRIX_ARRAY, PART_A_VERTEX_COUNTS);
        writeFixedVSweep(varyEFile, Variant.MATRIX_ARRAY);
        runBranchControl(controlFile);
    }

    /**
     * Part (b): adjacency lists and min-heap. (i) |V| is varied at the same
     * fixed |E| as part (a), with random weights; (ii) and (iii) vary |E| at a
     * fixed |V| with every weighting. Both use the seeds of part (a), so the
     * first trials of each configuration run on the same graphs.
     */
    private static void runPartB(Path varyVFile, Path varyEFile) throws IOException {
        System.out.println("Running Part (b): adjacency lists + min-heap...");
        writeFixedESweep(varyVFile, Variant.LIST_HEAP, PART_B_VERTEX_COUNTS);
        writeFixedVSweep(varyEFile, Variant.LIST_HEAP);
    }

    /**
     * Varies |V| at FIXED_EDGE_COUNT with random weights: sub-experiment (i).
     * Fails before measuring anything if some V cannot have that many edges.
     */
    private static void writeFixedESweep(Path outputFile, Variant variant, int[] vertexCounts)
            throws IOException {
        long e = FIXED_EDGE_COUNT;
        for (int v : vertexCounts) {
            if (e < v || e > GraphGenerator.maxEdges(v)) {
                throw new IllegalStateException(
                        variant.label + " needs V <= E <= V(V-1), but V = " + v
                                + " and E = " + e + ".");
            }
        }

        try (PrintWriter writer = newWriter(outputFile)) {
            writeSweepHeader(writer);
            for (int v : vertexCounts) {
                writeSweepRow(writer, Weights.RANDOM, "fixed_e", v, e,
                        measure(Weights.RANDOM, variant, v, e));
            }
        }
    }

    /**
     * Varies |E| at FIXED_VERTEX_COUNT with every weighting. The random rows
     * are sub-experiment (ii); all three weightings together are (iii).
     */
    private static void writeFixedVSweep(Path outputFile, Variant variant) throws IOException {
        try (PrintWriter writer = newWriter(outputFile)) {
            writeSweepHeader(writer);
            for (Weights weights : Weights.values()) {
                for (int outDegree : FIXED_V_OUT_DEGREES) {
                    long e = (long) outDegree * FIXED_VERTEX_COUNT;
                    writeSweepRow(writer, weights, "fixed_v", FIXED_VERTEX_COUNT, e,
                            measure(weights, variant, FIXED_VERTEX_COUNT, e));
                }
            }
        }
    }

    /** Runs one implementation on fresh seeded graphs of the given size. */
    private static Metrics measure(Weights weights, Variant variant, int v, long e) {
        TrialLog log = new TrialLog();
        long measuredNs = 0;
        int trials = 0;

        while (needsMoreTrials(trials, measuredNs)) {
            AdjacencyListGraph lists = weights.build(v, e, seedFor(v, e, trials));
            AdjacencyMatrixGraph matrix = variant.usesMatrix
                    ? AdjacencyMatrixGraph.from(lists)
                    : null;

            Execution execution = execute(variant, lists, matrix);
            ShortestPathVerifier.verify(lists, execution.paths());
            log.record(trials, execution);
            measuredNs += execution.cpuTimeNs();
            trials++;
        }

        Metrics metrics = log.summarise(trials);
        System.out.printf(
                Locale.ROOT, "  %-20s %-10s V = %,9d  E = %,11d  median %10.3f ms  (%d trials)%n",
                variant.label, weights.label, v, e, metrics.getMedianCpuTimeMs(), trials);
        return metrics;
    }

    /**
     * The branch control of part (a): DijkstraMatrixArray, whose matrix scan
     * has no if-statement on the edge test, against MatrixScanControl, which
     * does the same work with "if there is an edge" as its own if-statement,
     * on the same random graphs. The order alternates between trials.
     */
    private static void runBranchControl(Path outputFile) throws IOException {
        System.out.println("Running Part (a) branch control...");
        int v = CONTROL_VERTEX_COUNT;

        try (PrintWriter writer = newWriter(outputFile)) {
            writer.println(
                    "v,e,density,trials,median_cpu_time_ms_no_if,"
                            + "median_cpu_time_ms_separate_if,median_ratio_separate_if_to_no_if");
            for (double requestedDensity : CONTROL_DENSITIES) {
                long e = edgesForDensity(v, requestedDensity);
                long[] noIfNs = new long[MINIMUM_TRIALS];
                long[] separateIfNs = new long[MINIMUM_TRIALS];
                double[] ratios = new double[MINIMUM_TRIALS];

                for (int trial = 0; trial < MINIMUM_TRIALS; trial++) {
                    AdjacencyListGraph lists = Weights.RANDOM.build(v, e, seedFor(v, e, trial));
                    AdjacencyMatrixGraph matrix = AdjacencyMatrixGraph.from(lists);
                    boolean separateIfFirst = trial % 2 == 1;

                    if (separateIfFirst) {
                        separateIfNs[trial] = timeSeparateIf(lists, matrix);
                    }
                    Execution noIf = execute(Variant.MATRIX_ARRAY, lists, matrix);
                    ShortestPathVerifier.verify(lists, noIf.paths());
                    noIfNs[trial] = noIf.cpuTimeNs();
                    if (!separateIfFirst) {
                        separateIfNs[trial] = timeSeparateIf(lists, matrix);
                    }
                    ratios[trial] = (double) separateIfNs[trial] / noIfNs[trial];
                }

                double noIfMs = median(toDoubles(noIfNs)) / 1_000_000.0;
                double separateIfMs = median(toDoubles(separateIfNs)) / 1_000_000.0;
                writer.printf(
                        Locale.ROOT, "%d,%d,%.6f,%d,%.4f,%.4f,%.4f%n",
                        v, e, (double) e / GraphGenerator.maxEdges(v), MINIMUM_TRIALS,
                        noIfMs, separateIfMs, median(ratios));
                writer.flush();
                System.out.printf(
                        Locale.ROOT, "  V = %,d  density %.3f  no if %8.3f ms  "
                                + "separate if %8.3f ms%n",
                        v, (double) e / GraphGenerator.maxEdges(v), noIfMs, separateIfMs);
            }
        }
    }

    private static long timeSeparateIf(AdjacencyListGraph lists, AdjacencyMatrixGraph matrix) {
        long start = currentThreadCpuTime();
        ShortestPaths paths = MatrixScanControl.run(matrix, SOURCE_VERTEX);
        long elapsed = currentThreadCpuTime() - start;
        ShortestPathVerifier.verify(lists, paths);
        return elapsed;
    }

    /**
     * Part (c): every implementation on the same graphs, over a grid of
     * weighting, |V| and density. Within a trial all four run on one graph in
     * a shuffled order, so slow changes during the run (JIT compilation, CPU
     * clock speed) are not charged to whichever would otherwise go first, and
     * their times can be compared pairwise on the same graph.
     */
    private static void runPartC(Path comparisonFile, Path crossoverFile) throws IOException {
        System.out.println("Running Part (c): all implementations on the same graphs...");
        Variant[] variants = Variant.values();

        try (PrintWriter comparisonWriter = newWriter(comparisonFile);
                PrintWriter crossoverWriter = newWriter(crossoverFile)) {
            comparisonWriter.println(
                    "weights,algorithm,v,e,density,trials,mean_queue_comparisons,"
                            + "mean_relaxations,mean_decrease_keys,mean_entries_scanned,"
                            + "mean_cpu_time_ms,median_cpu_time_ms,stddev_cpu_time_ms,"
                            + "median_ratio_to_a");
            crossoverWriter.println(
                    "weights,v,crossings,density_below,b_over_a_below,"
                            + "density_above,b_over_a_above,crossover_density");

            for (Weights weights : PART_C_WEIGHTS) {
                for (int v : PART_C_VERTEX_COUNTS) {
                    double[] densities = new double[PART_C_DENSITIES.length];
                    double[] heapRatios = new double[PART_C_DENSITIES.length];

                    for (int index = 0; index < PART_C_DENSITIES.length; index++) {
                        long e = edgesForDensity(v, PART_C_DENSITIES[index]);
                        densities[index] = (double) e / GraphGenerator.maxEdges(v);
                        Metrics[] metrics = new Metrics[variants.length];
                        double[] ratios = new double[variants.length];
                        measureAllVariants(weights, v, e, variants, metrics, ratios);

                        for (int k = 0; k < variants.length; k++) {
                            comparisonWriter.printf(
                                    Locale.ROOT,
                                    "%s,%s,%d,%d,%.6f,%d,%.2f,%.2f,%.2f,%.2f,%.4f,%.4f,%.4f,%.4f%n",
                                    weights.label, variants[k].label, v, e, densities[index],
                                    metrics[k].getTrials(),
                                    metrics[k].getMeanQueueComparisons(),
                                    metrics[k].getMeanRelaxations(),
                                    metrics[k].getMeanDecreaseKeys(),
                                    metrics[k].getMeanEntriesScanned(),
                                    metrics[k].getMeanCpuTimeMs(),
                                    metrics[k].getMedianCpuTimeMs(),
                                    metrics[k].getCpuTimeStandardDeviationMs(),
                                    ratios[k]);
                        }
                        comparisonWriter.flush();
                        heapRatios[index] = ratios[Variant.LIST_HEAP.ordinal()];
                        System.out.printf(
                                Locale.ROOT,
                                "  %-10s V = %,6d  density %.3f  (a) %9.3f ms  (b) %9.3f ms  "
                                        + "(b)/(a) = %.3f%n",
                                weights.label, v, densities[index],
                                metrics[Variant.MATRIX_ARRAY.ordinal()].getMedianCpuTimeMs(),
                                metrics[Variant.LIST_HEAP.ordinal()].getMedianCpuTimeMs(),
                                heapRatios[index]);
                    }
                    writeCrossover(crossoverWriter, weights, v, densities, heapRatios);
                }
            }
        }
    }

    /**
     * Runs every variant on the same fresh graphs. {@code ratios[k]} receives
     * the median over trials of variant k's time divided by the time of
     * matrix + array on the same graph.
     */
    private static void measureAllVariants(
            Weights weights, int v, long e, Variant[] variants,
            Metrics[] metrics, double[] ratios) {
        int count = variants.length;
        TrialLog[] logs = new TrialLog[count];
        double[][] pairedRatios = new double[count][MAXIMUM_TRIALS];
        for (int k = 0; k < count; k++) {
            logs[k] = new TrialLog();
        }

        List<Variant> executionOrder = new ArrayList<>(Arrays.asList(variants));
        Random shuffleSource = new Random(EXECUTION_ORDER_SEED + 31L * v + e);
        long measuredNs = 0;
        int trials = 0;

        while (needsMoreTrials(trials, measuredNs)) {
            AdjacencyListGraph lists = weights.build(v, e, seedFor(v, e, trials));
            AdjacencyMatrixGraph matrix = AdjacencyMatrixGraph.from(lists);
            ShortestPaths reference = null;
            Variant referenceVariant = null;
            long[] timesNs = new long[count];

            Collections.shuffle(executionOrder, shuffleSource);
            for (Variant variant : executionOrder) {
                Execution execution = execute(variant, lists, matrix);
                ShortestPathVerifier.verify(lists, execution.paths());
                if (reference == null) {
                    reference = execution.paths();
                    referenceVariant = variant;
                } else if (!reference.sameDistances(execution.paths())) {
                    throw new IllegalStateException(
                            variant.label + " disagrees with " + referenceVariant.label
                                    + " at V=" + v + ", E=" + e + ", " + weights.label
                                    + " weights, trial " + trials);
                }
                logs[variant.ordinal()].record(trials, execution);
                timesNs[variant.ordinal()] = execution.cpuTimeNs();
                measuredNs += execution.cpuTimeNs();
            }

            double baseline = timesNs[Variant.MATRIX_ARRAY.ordinal()];
            for (int k = 0; k < count; k++) {
                pairedRatios[k][trials] = timesNs[k] / baseline;
            }
            trials++;
        }

        for (int k = 0; k < count; k++) {
            metrics[k] = logs[k].summarise(trials);
            ratios[k] = median(Arrays.copyOf(pairedRatios[k], trials));
        }
    }

    /**
     * Records where (b) stops being faster than (a). The bracket is the first
     * pair of neighbouring grid densities where the (b)/(a) ratio goes from
     * below 1 to at least 1; crossover_density interpolates linearly in
     * log(density) inside that bracket only. crossings counts how many times
     * the ratio passes 1 in either direction, so a noisy curve is visible.
     * The bracket columns are empty when the ratio never goes from below 1 to
     * 1 or above between neighbouring grid densities, for example when (b) is
     * faster on the whole grid.
     */
    private static void writeCrossover(
            PrintWriter writer, Weights weights, int v, double[] densities, double[] ratios) {
        int crossings = 0;
        int bracket = -1;
        for (int i = 0; i + 1 < densities.length; i++) {
            if ((ratios[i] < 1.0) != (ratios[i + 1] < 1.0)) {
                crossings++;
                if (bracket < 0 && ratios[i] < 1.0) {
                    bracket = i;
                }
            }
        }

        if (bracket < 0) {
            writer.printf(Locale.ROOT, "%s,%d,%d,,,,,%n", weights.label, v, crossings);
            return;
        }
        double below = densities[bracket];
        double above = densities[bracket + 1];
        double fraction = (1.0 - ratios[bracket]) / (ratios[bracket + 1] - ratios[bracket]);
        double crossover = Math.exp(
                Math.log(below) + fraction * (Math.log(above) - Math.log(below)));
        writer.printf(
                Locale.ROOT, "%s,%d,%d,%.4f,%.4f,%.4f,%.4f,%.4f%n",
                weights.label, v, crossings, below, ratios[bracket],
                above, ratios[bracket + 1], crossover);
    }

    private static boolean needsMoreTrials(int trials, long measuredNs) {
        return trials < MAXIMUM_TRIALS
                && (trials < MINIMUM_TRIALS || measuredNs < MINIMUM_MEASURED_NS);
    }

    /**
     * Runs one variant once and measures the current-thread CPU time of the
     * {@code run} call alone. CPU time leaves out time the thread is not
     * running, such as GC pauses or another process holding the core. It does
     * not remove slowdowns from shared caches or from running on an efficiency
     * core, so the experiment should run on an otherwise idle machine.
     */
    private static Execution execute(
            Variant variant, AdjacencyListGraph lists, AdjacencyMatrixGraph matrix) {
        switch (variant) {
            case MATRIX_ARRAY: {
                DijkstraMatrixArray dijkstra = new DijkstraMatrixArray();
                long start = currentThreadCpuTime();
                ShortestPaths paths = dijkstra.run(matrix, SOURCE_VERTEX);
                long elapsed = currentThreadCpuTime() - start;
                return new Execution(paths, dijkstra.getQueueComparisonCount(),
                        dijkstra.getRelaxationCount(), dijkstra.getDecreaseKeyCount(),
                        dijkstra.getEntriesScanned(), elapsed);
            }
            case LIST_HEAP: {
                DijkstraListHeap dijkstra = new DijkstraListHeap();
                long start = currentThreadCpuTime();
                ShortestPaths paths = dijkstra.run(lists, SOURCE_VERTEX);
                long elapsed = currentThreadCpuTime() - start;
                return new Execution(paths, dijkstra.getQueueComparisonCount(),
                        dijkstra.getRelaxationCount(), dijkstra.getDecreaseKeyCount(),
                        dijkstra.getEntriesScanned(), elapsed);
            }
            case MATRIX_HEAP: {
                DijkstraMatrixHeap dijkstra = new DijkstraMatrixHeap();
                long start = currentThreadCpuTime();
                ShortestPaths paths = dijkstra.run(matrix, SOURCE_VERTEX);
                long elapsed = currentThreadCpuTime() - start;
                return new Execution(paths, dijkstra.getQueueComparisonCount(),
                        dijkstra.getRelaxationCount(), dijkstra.getDecreaseKeyCount(),
                        dijkstra.getEntriesScanned(), elapsed);
            }
            case LIST_ARRAY: {
                DijkstraListArray dijkstra = new DijkstraListArray();
                long start = currentThreadCpuTime();
                ShortestPaths paths = dijkstra.run(lists, SOURCE_VERTEX);
                long elapsed = currentThreadCpuTime() - start;
                return new Execution(paths, dijkstra.getQueueComparisonCount(),
                        dijkstra.getRelaxationCount(), dijkstra.getDecreaseKeyCount(),
                        dijkstra.getEntriesScanned(), elapsed);
            }
            default:
                throw new IllegalArgumentException("Unknown variant " + variant);
        }
    }

    /** Compiles every implementation on sparse and dense graphs of every weighting before timing. */
    private static void warmUpJvm() {
        final int warmUpRuns = 5;
        final int warmUpSize = 2_000;
        long[] edgeCounts = {10L * warmUpSize, GraphGenerator.maxEdges(warmUpSize) / 2};

        for (Weights weights : Weights.values()) {
            for (long e : edgeCounts) {
                AdjacencyListGraph lists = weights.build(warmUpSize, e, seedFor(warmUpSize, e, -1));
                AdjacencyMatrixGraph matrix = AdjacencyMatrixGraph.from(lists);
                for (int run = 0; run < warmUpRuns; run++) {
                    for (Variant variant : Variant.values()) {
                        execute(variant, lists, matrix);
                    }
                    MatrixScanControl.run(matrix, SOURCE_VERTEX);
                }
            }
        }
    }

    private static void writeSweepHeader(PrintWriter writer) {
        writer.println(
                "weights,family,v,e,density,trials,mean_queue_comparisons,mean_relaxations,"
                        + "mean_decrease_keys,mean_entries_scanned,mean_cpu_time_ms,"
                        + "median_cpu_time_ms,stddev_cpu_time_ms");
    }

    private static void writeSweepRow(
            PrintWriter writer, Weights weights, String family, int v, long e, Metrics metrics) {
        writer.printf(
                Locale.ROOT,
                "%s,%s,%d,%d,%.6f,%d,%.2f,%.2f,%.2f,%.2f,%.4f,%.4f,%.4f%n",
                weights.label,
                family,
                v,
                e,
                (double) e / GraphGenerator.maxEdges(v),
                metrics.getTrials(),
                metrics.getMeanQueueComparisons(),
                metrics.getMeanRelaxations(),
                metrics.getMeanDecreaseKeys(),
                metrics.getMeanEntriesScanned(),
                metrics.getMeanCpuTimeMs(),
                metrics.getMedianCpuTimeMs(),
                metrics.getCpuTimeStandardDeviationMs());
        writer.flush();
    }

    /** The edge count closest to the requested density, but at least V so the cycle fits. */
    private static long edgesForDensity(int v, double density) {
        long e = Math.round(density * GraphGenerator.maxEdges(v));
        return Math.max(v, Math.min(e, GraphGenerator.maxEdges(v)));
    }

    private static long seedFor(int v, long e, int trial) {
        return BASE_RANDOM_SEED + 1_000_003L * v + 7_919L * e + trial;
    }

    private static double[] toDoubles(long[] values) {
        double[] result = new double[values.length];
        for (int i = 0; i < values.length; i++) {
            result[i] = values[i];
        }
        return result;
    }

    private static double median(double[] values) {
        double[] sortedValues = Arrays.copyOf(values, values.length);
        Arrays.sort(sortedValues);
        int middle = sortedValues.length / 2;

        if (sortedValues.length % 2 == 1) {
            return sortedValues[middle];
        }
        return (sortedValues[middle - 1] + sortedValues[middle]) / 2.0;
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

    private static PrintWriter newWriter(Path outputFile) throws IOException {
        return new PrintWriter(Files.newBufferedWriter(
                outputFile, StandardCharsets.UTF_8));
    }
}
