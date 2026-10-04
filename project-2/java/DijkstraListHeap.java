import java.util.Arrays;

/**
 * Part (b): Dijkstra's algorithm with the graph in an array of adjacency lists
 * and the priority queue in a binary min-heap.
 *
 * <p>Build-Min-Heap is O(V), the V extractions are O(log V) each, and every
 * edge is read once from its list and may cause one O(log V) decreaseKey. The
 * running time is therefore O((V + E) log V).
 */
public final class DijkstraListHeap {
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

        MinHeapPriorityQueue queue = new MinHeapPriorityQueue(distance);
        long relaxations = 0;
        long decreaseKeys = 0;

        while (!queue.isEmpty()) {
            int u = queue.extractMin();
            long distanceU = distance[u];
            if (distanceU == ShortestPaths.UNREACHABLE) {
                break;
            }

            // As in CLRS, every edge of u is relaxed, including edges into
            // finished vertices: their distance is already <= d[u], so with
            // positive weights the test fails and decreaseKey is never called
            // for a vertex that has left the heap.
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

    /** Key comparisons made inside the heap: Build-Min-Heap, extractMin and decreaseKey. */
    public long getQueueComparisonCount() {
        return queueComparisonCount;
    }

    /** Edges relaxed, one key comparison each: E when every vertex is reachable. */
    public long getRelaxationCount() {
        return relaxationCount;
    }

    /** Relaxations that found a shorter path and lowered a key. */
    public long getDecreaseKeyCount() {
        return decreaseKeyCount;
    }

    /** Adjacency list entries read: one per edge, so the same as the relaxation count. */
    public long getEntriesScanned() {
        return entriesScanned;
    }
}
