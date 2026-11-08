import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Prints the running results of part (4) and writes the CSV files in results/:
 * the full table of each instance, and the subproblem graph of part (2).
 */
public class ExperimentRunner {
    private static final int[] FIRST_WEIGHTS = {4, 5, 8};
    private static final int[] FIRST_PROFITS = {7, 6, 9};
    private static final int[] SECOND_WEIGHTS = {5, 6, 7};
    private static final int[] SECOND_PROFITS = {7, 6, 9};
    private static final int GRAPH_CAPACITY = 15;

    public static void main(String[] args) throws IOException {
        Path outputDirectory = args.length == 0 ? Paths.get("results") : Paths.get(args[0]);
        Files.createDirectories(outputDirectory);

        writeSubproblemGraph(outputDirectory);
        runInstance("Part (4a)", FIRST_WEIGHTS, FIRST_PROFITS, 16,
                outputDirectory.resolve("part_4a_P16.csv"));
        System.out.println();
        runInstance("Part (4b)", SECOND_WEIGHTS, SECOND_PROFITS, 12,
                outputDirectory.resolve("part_4b_P12.csv"));
    }

    private static void writeSubproblemGraph(Path outputDirectory) throws IOException {
        KnapsackSolution solution =
                UnboundedKnapsack.solve(FIRST_WEIGHTS, FIRST_PROFITS, GRAPH_CAPACITY);
        List<Integer> vertices = UnboundedKnapsack.subproblemsOf(FIRST_WEIGHTS, GRAPH_CAPACITY);

        try (PrintWriter vertexWriter = newWriter(outputDirectory.resolve("part_2_vertices.csv"));
                PrintWriter edgeWriter = newWriter(outputDirectory.resolve("part_2_edges.csv"))) {
            vertexWriter.println("capacity,max_profit");
            edgeWriter.println("capacity,subproblem,object_type,weight,profit");
            for (int c : vertices) {
                vertexWriter.println(c + "," + solution.profitAt(c));
                for (int i = 0; i < FIRST_WEIGHTS.length; i++) {
                    if (FIRST_WEIGHTS[i] <= c) {
                        edgeWriter.println(c + "," + (c - FIRST_WEIGHTS[i]) + "," + i + ","
                                + FIRST_WEIGHTS[i] + "," + FIRST_PROFITS[i]);
                    }
                }
            }
        }
    }

    private static void runInstance(
            String title, int[] weights, int[] profits, int capacity, Path outputFile)
            throws IOException {
        KnapsackSolution solution = UnboundedKnapsack.solve(weights, profits, capacity);

        System.out.println(title + ": P(" + capacity + ")");
        System.out.println("  w = " + Arrays.toString(weights));
        System.out.println("  p = " + Arrays.toString(profits));
        System.out.println();
        StringBuilder header = new StringBuilder(String.format("  %3s %5s", "C", "P(C)"));
        for (int i = 0; i < weights.length; i++) {
            header.append(String.format(" %9s", profits[i] + "+P(C-" + weights[i] + ")"));
        }
        System.out.println(header.append("  taken"));

        try (PrintWriter writer = newWriter(outputFile)) {
            StringBuilder csvHeader = new StringBuilder("capacity,max_profit");
            for (int i = 0; i < weights.length; i++) {
                csvHeader.append(",candidate_type_").append(i);
            }
            writer.println(csvHeader.append(",type_taken"));

            for (int c = 0; c <= capacity; c++) {
                StringBuilder row =
                        new StringBuilder(String.format("  %3d %5d", c, solution.profitAt(c)));
                StringBuilder csvRow =
                        new StringBuilder().append(c).append(',').append(solution.profitAt(c));
                for (int i = 0; i < weights.length; i++) {
                    boolean fits = weights[i] <= c;
                    String candidate =
                            fits ? Long.toString(profits[i] + solution.profitAt(c - weights[i])) : "";
                    row.append(String.format(" %9s", fits ? candidate : "-"));
                    csvRow.append(',').append(candidate);
                }
                int taken = solution.objectTakenAt(c);
                boolean none = taken == KnapsackSolution.NO_OBJECT;
                System.out.println(row.append(String.format("  %5s", none ? "-" : taken)));
                writer.println(csvRow.append(',').append(none ? "" : taken));
            }
        }

        System.out.println();
        System.out.println("  P(" + capacity + ") = " + solution.maxProfit());
        System.out.println("  optimal packing: " + describePacking(solution, weights, profits));
    }

    private static String describePacking(
            KnapsackSolution solution, int[] weights, int[] profits) {
        int[] counts = solution.objectCounts();
        List<String> parts = new ArrayList<>();
        int totalWeight = 0;
        int totalProfit = 0;
        for (int i = 0; i < counts.length; i++) {
            if (counts[i] > 0) {
                parts.add(counts[i] + " x type " + i);
                totalWeight += counts[i] * weights[i];
                totalProfit += counts[i] * profits[i];
            }
        }
        return String.join(" + ", parts)
                + " (total weight " + totalWeight + ", total profit " + totalProfit + ")";
    }

    private static PrintWriter newWriter(Path outputFile) throws IOException {
        return new PrintWriter(Files.newBufferedWriter(outputFile, StandardCharsets.UTF_8));
    }
}
