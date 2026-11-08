/** The table of maximum profits produced by one run of the dynamic program. */
public final class KnapsackSolution {
    public static final int NO_OBJECT = -1;

    private final int[] weights;
    private final long[] profit;
    private final int[] objectTaken;

    public KnapsackSolution(int[] weights, long[] profit, int[] objectTaken) {
        this.weights = weights;
        this.profit = profit;
        this.objectTaken = objectTaken;
    }

    public int capacity() {
        return profit.length - 1;
    }

    public long maxProfit() {
        return profit[capacity()];
    }

    public long profitAt(int c) {
        return profit[c];
    }

    /** The type of the object that gives P(c), or NO_OBJECT if no object fits in c. */
    public int objectTakenAt(int c) {
        return objectTaken[c];
    }

    /** How many objects of each type one optimal packing of the full capacity uses. */
    public int[] objectCounts() {
        int[] counts = new int[weights.length];
        int c = capacity();
        while (objectTaken[c] != NO_OBJECT) {
            counts[objectTaken[c]]++;
            c -= weights[objectTaken[c]];
        }
        return counts;
    }
}
