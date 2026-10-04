import java.util.Arrays;

/**
 * Part (c) control: the adjacency matrix of part (a) with the min-heap of
 * part (b). Reading every matrix row still costs Theta(V^2), and each edge may
 * add an O(log V) decreaseKey, so the running time is O(V^2 + E log V).
 *
 * <p>Together with {@link DijkstraListArray}, this separates the effect of the
 * graph representation from the effect of the priority queue. The loop is the
 * same as in {@link DijkstraMatrixArray}; see the comments there.
 */
public final class DijkstraMatrixHeap {
    private long queueComparisonCount;
    private long relaxationCount;
    private long decreaseKeyCount;
    private long entriesScanned;

    public ShortestPaths run(AdjacencyMatrixGraph graph, int source) {
        int vertexCount = graph.vertexCount();
        if (source < 0 || source >= vertexCount) {
            throw new IllegalArgumentException("Source vertex is outside the graph.");
        }

        long[] distance = new long[vertexCount];
        int[] predecessor = new int[vertexCount];
        Arrays.fill(distance, ShortestPaths.UNREACHABLE);
        Arrays.fill(predecessor, ShortestPaths.NO_PREDECESSOR);
        distance[source] = 0;

        MinHeapPriorityQueue queue = new MinHeapPriorityQueue(distance);
        long relaxations = 0;
        long decreaseKeys = 0;
        long scanned = 0;

        while (!queue.isEmpty()) {
            int u = queue.extractMin();
            long distanceU = distance[u];
            if (distanceU == ShortestPaths.UNREACHABLE) {
                break;
            }

            int[] row = graph.row(u);
            for (int v = 0; v < vertexCount; v++) {
                long candidate = AdjacencyMatrixGraph.distanceThrough(distanceU, row[v]);
                if (candidate < distance[v]) {
                    distance[v] = candidate;
                    predecessor[v] = u;
                    queue.decreaseKey(v, candidate);
                    decreaseKeys++;
                }
            }
            scanned += vertexCount;
            relaxations += graph.outDegree(u);
        }

        queueComparisonCount = queue.getComparisonCount();
        relaxationCount = relaxations;
        decreaseKeyCount = decreaseKeys;
        entriesScanned = scanned;
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
