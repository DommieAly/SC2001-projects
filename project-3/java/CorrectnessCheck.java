import java.util.Arrays;
import java.util.List;
import java.util.SplittableRandom;

/**
 * Checks the dynamic program before its results are reported.
 *
 * <ol>
 *   <li>Both instances of part (4), against tables worked out by hand.</li>
 *   <li>The subproblem graph of P(15) from part (2).</li>
 *   <li>Random instances against an exhaustive search of every multiset of
 *       objects, which shares no code with the recurrence, at every capacity.
 *       The packing the dynamic program reports must fit and earn P(C).</li>
 * </ol>
 */
public final class CorrectnessCheck {
    private static final int[] FIRST_WEIGHTS = {4, 5, 8};
    private static final int[] FIRST_PROFITS = {7, 6, 9};
    private static final int[] SECOND_WEIGHTS = {5, 6, 7};
    private static final int[] SECOND_PROFITS = {7, 6, 9};

    private CorrectnessCheck() {
    }

    public static void main(String[] args) {
        checkHandWorkedTables();
        checkSubproblemGraph();
        checkAgainstExhaustiveSearch();
        System.out.println("All correctness checks passed.");
    }

    private static void checkHandWorkedTables() {
        checkTable(FIRST_WEIGHTS, FIRST_PROFITS,
                new long[] {0, 0, 0, 0, 7, 7, 7, 7, 14, 14, 14, 14, 21, 21, 21, 21, 28},
                new int[] {4, 0, 0});
        checkTable(SECOND_WEIGHTS, SECOND_PROFITS,
                new long[] {0, 0, 0, 0, 0, 7, 7, 9, 9, 9, 14, 14, 16},
                new int[] {1, 0, 1});
        System.out.println("  hand-worked tables: P(16) = 28 and P(12) = 16");
    }

    private static void checkTable(int[] weights, int[] profits, long[] expected, int[] counts) {
        KnapsackSolution solution = UnboundedKnapsack.solve(weights, profits, expected.length - 1);
        for (int c = 0; c < expected.length; c++) {
            if (solution.profitAt(c) != expected[c]) {
                throw new IllegalStateException("Hand-worked table: wrong P(" + c + ").");
            }
        }
        if (!Arrays.equals(solution.objectCounts(), counts)) {
            throw new IllegalStateException("Hand-worked table: wrong packing.");
        }
    }

    private static void checkSubproblemGraph() {
        List<Integer> vertices = UnboundedKnapsack.subproblemsOf(FIRST_WEIGHTS, 15);
        if (!vertices.equals(List.of(15, 11, 10, 7, 6, 5, 3, 2, 1, 0))) {
            throw new IllegalStateException("Wrong vertices in the subproblem graph: " + vertices);
        }
        int edges = 0;
        for (int c : vertices) {
            for (int weight : FIRST_WEIGHTS) {
                if (weight <= c) {
                    edges++;
                }
            }
        }
        if (edges != 15) {
            throw new IllegalStateException("Wrong edge count in the subproblem graph: " + edges);
        }
        System.out.println("  subproblem graph of P(15): " + vertices.size() + " vertices, "
                + edges + " edges");
    }

    private static void checkAgainstExhaustiveSearch() {
        SplittableRandom random = new SplittableRandom(20261108L);
        int instances = 0;

        for (int types = 1; types <= 6; types++) {
            for (int maxWeight : new int[] {3, 8, 20}) {
                for (int maxProfit : new int[] {1, 10, 1_000}) {
                    for (int capacity : new int[] {1, 2, 7, 20, 40}) {
                        for (int repeat = 0; repeat < 20; repeat++) {
                            int[] weights = random.ints(types, 1, maxWeight + 1).toArray();
                            int[] profits = random.ints(types, 1, maxProfit + 1).toArray();
                            checkInstance(weights, profits, capacity);
                            instances++;
                        }
                    }
                }
            }
        }
        System.out.println("  exhaustive search: " + instances + " random instances agree");
    }

    private static void checkInstance(int[] weights, int[] profits, int capacity) {
        KnapsackSolution solution = UnboundedKnapsack.solve(weights, profits, capacity);
        long[] expected = exhaustiveTable(weights, profits, capacity);
        for (int c = 0; c <= capacity; c++) {
            if (solution.profitAt(c) != expected[c]) {
                throw new IllegalStateException("Wrong P(" + c + ") for weights "
                        + Arrays.toString(weights) + ", profits " + Arrays.toString(profits));
            }
        }

        int[] counts = solution.objectCounts();
        long totalWeight = 0;
        long totalProfit = 0;
        for (int i = 0; i < counts.length; i++) {
            totalWeight += (long) counts[i] * weights[i];
            totalProfit += (long) counts[i] * profits[i];
        }
        if (totalWeight > capacity || totalProfit != solution.maxProfit()) {
            throw new IllegalStateException("Reported packing does not match P(C).");
        }
    }

    /** The maximum profit at every capacity from 0 to capacity, by trying every multiset. */
    private static long[] exhaustiveTable(int[] weights, int[] profits, int capacity) {
        long[] bestAtExactWeight = new long[capacity + 1];
        enumerate(weights, profits, 0, 0, 0, bestAtExactWeight);

        // A packing of weight x also fits in every capacity of at least x.
        long[] table = new long[capacity + 1];
        long best = 0;
        for (int c = 0; c <= capacity; c++) {
            best = Math.max(best, bestAtExactWeight[c]);
            table[c] = best;
        }
        return table;
    }

    private static void enumerate(
            int[] weights, int[] profits, int type, int weight, long profit, long[] best) {
        if (type == weights.length) {
            best[weight] = Math.max(best[weight], profit);
            return;
        }
        for (int count = 0; weight + count * weights[type] < best.length; count++) {
            enumerate(weights, profits, type + 1,
                    weight + count * weights[type], profit + (long) count * profits[type], best);
        }
    }
}
