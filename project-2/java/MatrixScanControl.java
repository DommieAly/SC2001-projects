import java.util.Arrays;

/**
 * Part (a) control: {@link DijkstraMatrixArray} with the edge test written as
 * its own if-statement, the way the textbook reads ("for each v adjacent to
 * u"). It makes the same relaxations and decrease-keys. The work per cell
 * differs a little: it skips an empty cell after one test, while
 * DijkstraMatrixArray computes a bit mask, loads d[v] and compares in every
 * cell. Where the edge test is easy to guess (a nearly empty or nearly full
 * row) the control is therefore somewhat faster; in a random row of density
 * 0.5 its wrong guesses make it several times slower.
 *
 * <p>The CPU guesses the outcome of each branch before it is known and throws
 * away its work when the guess is wrong. "Is there an edge in this cell?" is
 * easy to guess in a nearly empty or nearly full row, and a coin flip in a
 * random row of density 0.5.
 */
public final class MatrixScanControl {
    private MatrixScanControl() {
    }

    public static ShortestPaths run(AdjacencyMatrixGraph graph, int source) {
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
        while (!queue.isEmpty()) {
            int u = queue.extractMin();
            long distanceU = distance[u];
            if (distanceU == ShortestPaths.UNREACHABLE) {
                break;
            }

            int[] row = graph.row(u);
            for (int v = 0; v < vertexCount; v++) {
                int weight = row[v];
                if (weight != AdjacencyMatrixGraph.NO_EDGE) {
                    long candidate = distanceU + weight;
                    if (candidate < distance[v]) {
                        distance[v] = candidate;
                        predecessor[v] = u;
                        queue.decreaseKey(v, candidate);
                    }
                }
            }
        }
        return new ShortestPaths(source, distance, predecessor);
    }
}
