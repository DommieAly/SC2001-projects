/**
 * A directed graph with positive integer weights, stored as an array of
 * adjacency lists (lecture 4): for every vertex u, {@code neighbours[u]} holds
 * the vertices that u has an edge to and {@code weights[u]} the matching edge
 * weights. Memory is Theta(V + E).
 */
public final class AdjacencyListGraph {
    private final int[][] neighbours;
    private final int[][] weights;
    private final long edgeCount;

    public AdjacencyListGraph(int[][] neighbours, int[][] weights) {
        if (neighbours == null || weights == null || neighbours.length != weights.length) {
            throw new IllegalArgumentException(
                    "There must be one neighbour list and one weight list per vertex.");
        }

        int vertexCount = neighbours.length;
        long edges = 0;
        for (int u = 0; u < vertexCount; u++) {
            if (neighbours[u].length != weights[u].length) {
                throw new IllegalArgumentException(
                        "Vertex " + u + " has a different number of neighbours and weights.");
            }
            for (int i = 0; i < neighbours[u].length; i++) {
                if (neighbours[u][i] < 0 || neighbours[u][i] >= vertexCount) {
                    throw new IllegalArgumentException(
                            "Edge from " + u + " points outside the graph.");
                }
                if (weights[u][i] <= 0) {
                    throw new IllegalArgumentException("Edge weights must be positive.");
                }
            }
            edges += neighbours[u].length;
        }

        this.neighbours = neighbours;
        this.weights = weights;
        this.edgeCount = edges;
    }

    public int vertexCount() {
        return neighbours.length;
    }

    public long edgeCount() {
        return edgeCount;
    }

    /** The vertices u has an edge to. The array is shared, so callers must not modify it. */
    public int[] neighboursOf(int u) {
        return neighbours[u];
    }

    /** The weights of u's edges, in the same order as {@link #neighboursOf(int)}. */
    public int[] weightsOf(int u) {
        return weights[u];
    }
}
