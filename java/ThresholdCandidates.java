import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The thresholds worth measuring for a given input size.
 *
 * <p>Merge sort halves every range, so the subarrays that actually reach
 * insertion sort have size {@code ceil(n / 2^depth)}, where depth is the level
 * at which the recursion stops. A threshold therefore only matters through the
 * depth it selects: every S in
 *
 * <pre>    ceil(n / 2^depth) &lt;= S &lt;= floor(n / 2^(depth-1)) - 1</pre>
 *
 * builds an identical recursion tree and performs an identical number of key
 * comparisons. Sweeping S one integer at a time therefore re-measures the same
 * computation hundreds of times over, while one representative per interval
 * covers every distinct behaviour in about log2(n) candidates.
 *
 * <p>The representative is the midpoint of the interval. The endpoints are
 * avoided because {@code floor(n / 2^(depth-1))} itself produces a ragged tree,
 * in which some branches stop one level earlier than the rest.
 */
public final class ThresholdCandidates {

    /** One halving interval, and the threshold chosen to stand for it. */
    public record Candidate(
            int depth,
            int intervalLow,
            int intervalHigh,
            int threshold,
            int leafSize) {
    }

    private ThresholdCandidates() {
    }

    /**
     * Enumerates the intervals from the deepest recursion, whose leaves hold a
     * single element, up to the last interval whose leaves do not exceed
     * {@code maximumLeafSize}. The result is ordered by increasing threshold.
     *
     * <p>The cap is not a shortcut. Part (c)(ii) establishes that the
     * comparison count rises monotonically once the insertion sort term
     * dominates, so intervals beyond the minimum can only be slower.
     */
    public static List<Candidate> forInputSize(int inputSize, int maximumLeafSize) {
        if (inputSize < 1) {
            throw new IllegalArgumentException("Input size must be positive.");
        }
        if (maximumLeafSize < 1) {
            throw new IllegalArgumentException("Maximum leaf size must be positive.");
        }

        List<Candidate> candidates = new ArrayList<>();
        for (int depth = 1; ; depth++) {
            int leafSize = ceilDivide(inputSize, depth);
            int intervalLow = leafSize;
            int intervalHigh = Math.max(
                    intervalLow, floorDivide(inputSize, depth - 1) - 1);

            if (leafSize <= maximumLeafSize) {
                candidates.add(new Candidate(
                        depth,
                        intervalLow,
                        intervalHigh,
                        intervalLow + (intervalHigh - intervalLow) / 2,
                        leafSize));
            }
            if (leafSize <= 1) {
                break;
            }
        }

        Collections.reverse(candidates);
        return candidates;
    }

    private static int ceilDivide(int value, int depth) {
        long divisor = 1L << depth;
        return (int) ((value + divisor - 1) / divisor);
    }

    private static int floorDivide(int value, int depth) {
        long divisor = 1L << depth;
        return (int) (value / divisor);
    }
}
