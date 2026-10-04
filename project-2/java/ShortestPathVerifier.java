/**
 * Checks a shortest-path result against the graph in O(V + E), independently
 * of the algorithm that produced it.
 *
 * <p>A distance array d is exactly the shortest-path distance from s when
 * <ol>
 *   <li>d[s] = 0,</li>
 *   <li>no edge can be relaxed further: d[v] &lt;= d[u] + w(u, v) for every edge
 *       (u, v) with d[u] finite, and</li>
 *   <li>every other vertex v with finite d[v] has a predecessor p such that
 *       (p, v) is an edge and d[v] = d[p] + w(p, v).</li>
 * </ol>
 * Condition 2 means no path is shorter than d. Condition 3 means d is the
 * length of a real path: weights are positive, so following predecessors
 * strictly lowers d and must end at the only vertex without one, s. The
 * verifier also checks that s and every unreachable vertex have no
 * predecessor, so the predecessors form a tree rooted at s.
 */
public final class ShortestPathVerifier {
    private ShortestPathVerifier() {
    }

    public static void verify(AdjacencyListGraph graph, ShortestPaths paths) {
        int vertexCount = graph.vertexCount();
        int source = paths.source();
        if (paths.vertexCount() != vertexCount) {
            throw new IllegalStateException("Result has the wrong number of vertices.");
        }
        if (paths.distanceTo(source) != 0) {
            throw new IllegalStateException("Distance to the source is not 0.");
        }
        if (paths.predecessorOf(source) != ShortestPaths.NO_PREDECESSOR) {
            throw new IllegalStateException("The source has a predecessor.");
        }

        // predecessorEdgeMatches[v]: the edge from v's predecessor p satisfies d[v] = d[p] + w(p, v).
        boolean[] predecessorEdgeMatches = new boolean[vertexCount];
        for (int u = 0; u < vertexCount; u++) {
            long distanceU = paths.distanceTo(u);
            if (distanceU == ShortestPaths.UNREACHABLE) {
                continue;
            }
            int[] neighbours = graph.neighboursOf(u);
            int[] weights = graph.weightsOf(u);
            for (int i = 0; i < neighbours.length; i++) {
                int v = neighbours[i];
                long throughU = distanceU + weights[i];
                if (paths.distanceTo(v) > throughU) {
                    throw new IllegalStateException(
                            "Edge " + u + " -> " + v + " can still shorten the distance to " + v + ".");
                }
                if (paths.predecessorOf(v) == u && paths.distanceTo(v) == throughU) {
                    predecessorEdgeMatches[v] = true;
                }
            }
        }

        for (int v = 0; v < vertexCount; v++) {
            if (v == source) {
                continue;
            }
            if (paths.distanceTo(v) == ShortestPaths.UNREACHABLE) {
                if (paths.predecessorOf(v) != ShortestPaths.NO_PREDECESSOR) {
                    throw new IllegalStateException("Unreachable vertex " + v + " has a predecessor.");
                }
            } else if (!predecessorEdgeMatches[v]) {
                throw new IllegalStateException(
                        "Distance to " + v + " is not the length of a path through its predecessor.");
            }
        }
    }
}
