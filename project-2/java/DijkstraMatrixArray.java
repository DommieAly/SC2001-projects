import java.util.Arrays;

/**
 * Part (a): Dijkstra's algorithm with the graph in an adjacency matrix and the
 * priority queue in an unsorted array.
 *
 * <p>Iteration i finds the closest unfinished vertex u by scanning the V - i
 * entries still in the queue (V(V-1)/2 comparisons in all), then reads all V
 * cells of u's matrix row (V^2 cells in all) and relaxes every edge it finds
 * (E relaxations in all, each followed by at most one O(1) decreaseKey). The
 * running time is Theta(V^2 + E) = Theta(V^2) whatever E is, because
 * E &lt;= V(V-1). This assumes every vertex is reachable, as in all our
 * experiment graphs: the loop stops at the first unreachable vertex, so with
 * R reachable vertices it costs Theta(R * V).
 */
public final class DijkstraMatrixArray {
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

        ArrayPriorityQueue queue = new ArrayPriorityQueue(distance);
        long relaxations = 0;
        long decreaseKeys = 0;
        long scanned = 0;

        while (!queue.isEmpty()) {
            int u = queue.extractMin();
            long distanceU = distance[u];
            if (distanceU == ShortestPaths.UNREACHABLE) {
                break;
            }

            // As in CLRS, every edge of u is relaxed, including edges into
            // finished vertices: their distance is already <= d[u], so with
            // positive weights the test below fails for them. A cell without
            // an edge gives an infinite candidate, which fails the test too.
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

    /** Key comparisons made inside the priority queue: V(V-1)/2 for a full run. */
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

    /** Matrix cells read while looking for edges: V^2 for a full run. */
    public long getEntriesScanned() {
        return entriesScanned;
    }
}
