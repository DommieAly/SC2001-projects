import java.util.Arrays;

/**
 * Part (c) control: the adjacency lists of part (b) with the array priority
 * queue of part (a). Reading the lists costs only O(V + E), but every
 * extraction still scans the whole queue, so the running time is
 * Theta(V^2 + E) = Theta(V^2).
 *
 * <p>Together with {@link DijkstraMatrixHeap}, this separates the effect of the
 * graph representation from the effect of the priority queue. The loop is the
 * same as in {@link DijkstraListHeap}; see the comments there.
 */
public final class DijkstraListArray {
    private long queueComparisonCount;
    private long relaxationCount;
    private long decreaseKeyCount;
    private long entriesScanned;

    public ShortestPaths run(AdjacencyListGraph graph, int source) {
        int vertexCount = graph.vertexCount();
        if (source < 0 || source >= vertexCount) {
            throw new IllegalArgumentException("Source vertex is outside the graph.");
        }

        long[] distance = new long[vertexCount];
        int[] predecessor = new int[vertexCount];
        Arrays.fill(distance, ShortestPaths.UNREACHABLE);
        Arrays.fill(predecessor, ShortestPaths.NO_PREDECESSOR);
        distance[source] = 0;

        ArrayPriorityQueue queue = new ArrayPriorityQueue(distance);
        long relaxations = 0;
        long decreaseKeys = 0;

        while (!queue.isEmpty()) {
            int u = queue.extractMin();
            long distanceU = distance[u];
            if (distanceU == ShortestPaths.UNREACHABLE) {
                break;
            }

            int[] neighbours = graph.neighboursOf(u);
            int[] weights = graph.weightsOf(u);
            for (int i = 0; i < neighbours.length; i++) {
                int v = neighbours[i];
                long candidate = distanceU + weights[i];
                if (candidate < distance[v]) {
                    distance[v] = candidate;
                    predecessor[v] = u;
                    queue.decreaseKey(v, candidate);
                    decreaseKeys++;
                }
            }
            relaxations += neighbours.length;
        }

        queueComparisonCount = queue.getComparisonCount();
        relaxationCount = relaxations;
        decreaseKeyCount = decreaseKeys;
        entriesScanned = relaxations;
        return new ShortestPaths(source, distance, predecessor);
    }

    public long getQueueComparisonCount() {
        return queueComparisonCount;
    }

    public long getRelaxationCount() {
        return relaxationCount;
    }

    public long getDecreaseKeyCount() {
        return decreaseKeyCount;
    }

    public long getEntriesScanned() {
        return entriesScanned;
    }
}
