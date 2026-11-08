import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Part (3): the bottom-up dynamic program for the knapsack with unlimited
 * supplies of each object type.
 *
 * <p>P(c) = max { p[i] + P(c - w[i]) : w[i] <= c }, and P(c) = 0 when no object
 * fits. A subproblem is only a capacity, because taking an object leaves all n
 * types available, so the dictionary is one array indexed by capacity. Every
 * weight is at least 1, so P(c) depends only on smaller capacities and the
 * array can be filled from 0 upwards. The C outer iterations make n tests each:
 * Theta(nC) time and Theta(C) space.
 */
public final class UnboundedKnapsack {
    private UnboundedKnapsack() {
    }

    public static KnapsackSolution solve(int[] weights, int[] profits, int capacity) {
        validate(weights, profits, capacity);

        long[] profit = new long[capacity + 1];
        int[] objectTaken = new int[capacity + 1];
        Arrays.fill(objectTaken, KnapsackSolution.NO_OBJECT);

        for (int c = 1; c <= capacity; c++) {
            // Starting from 0 gives P(c) = 0 when no object fits. The smallest
            // weight can exceed 1, so unlike coin changing this case does occur.
            long best = 0;
            for (int i = 0; i < weights.length; i++) {
                if (weights[i] <= c && profits[i] + profit[c - weights[i]] > best) {
                    best = profits[i] + profit[c - weights[i]];
                    objectTaken[c] = i;
                }
            }
            profit[c] = best;
        }
        return new KnapsackSolution(weights, profit, objectTaken);
    }

    /**
     * The vertices of the subproblem graph of P(capacity), largest first: the
     * capacities that the recursive definition reaches from P(capacity). Each
     * vertex c has one edge to c - w[i] for every w[i] <= c.
     */
    public static List<Integer> subproblemsOf(int[] weights, int capacity) {
        boolean[] reached = new boolean[capacity + 1];
        reached[capacity] = true;
        List<Integer> subproblems = new ArrayList<>();
        for (int c = capacity; c >= 0; c--) {
            if (!reached[c]) {
                continue;
            }
            subproblems.add(c);
            for (int weight : weights) {
                if (weight <= c) {
                    reached[c - weight] = true;
                }
            }
        }
        return subproblems;
    }

    private static void validate(int[] weights, int[] profits, int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("The capacity must be a positive integer.");
        }
        if (weights.length == 0 || weights.length != profits.length) {
            throw new IllegalArgumentException("Need one weight and one profit per object type.");
        }
        for (int i = 0; i < weights.length; i++) {
            if (weights[i] < 1 || profits[i] < 1) {
                throw new IllegalArgumentException("Weights and profits must be positive integers.");
            }
        }
    }
}
