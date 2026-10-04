/**
 * A directed graph stored as a V x V adjacency matrix (lecture 4). Cell
 * {@code [u][v]} holds the weight of edge (u, v), or {@link #NO_EDGE} when there
 * is none. Weights are positive, so 0 is free to mean "no edge". Memory is
 * Theta(V^2) however few edges the graph has.
 */
public final class AdjacencyMatrixGraph {
    public static final int NO_EDGE = 0;

    private final int[][] matrix;
    private final int[] outDegree;
    private final long edgeCount;

    private AdjacencyMatrixGraph(int[][] matrix, int[] outDegree, long edgeCount) {
        this.matrix = matrix;
        this.outDegree = outDegree;
        this.edgeCount = edgeCount;
    }

    /**
     * Builds the matrix of the same graph that the adjacency lists describe, in
     * Theta(V^2 + E) time, so every Dijkstra implementation can run on the
     * same input.
     */
    public static AdjacencyMatrixGraph from(AdjacencyListGraph graph) {
        int vertexCount = graph.vertexCount();
        int[][] matrix = new int[vertexCount][vertexCount];
        int[] outDegree = new int[vertexCount];
        long edges = 0;

        for (int u = 0; u < vertexCount; u++) {
            int[] neighbours = graph.neighboursOf(u);
            int[] weights = graph.weightsOf(u);
            for (int i = 0; i < neighbours.length; i++) {
                if (matrix[u][neighbours[i]] != NO_EDGE) {
                    throw new IllegalArgumentException(
                            "An adjacency matrix cannot hold two edges from "
                                    + u + " to " + neighbours[i] + ".");
                }
                matrix[u][neighbours[i]] = weights[i];
            }
            outDegree[u] = neighbours.length;
            edges += neighbours.length;
        }
        return new AdjacencyMatrixGraph(matrix, outDegree, edges);
    }

    public int vertexCount() {
        return matrix.length;
    }

    public long edgeCount() {
        return edgeCount;
    }

    /** The number of edges leaving u, i.e. the non-empty cells in row u. */
    public int outDegree(int u) {
        return outDegree[u];
    }

    /**
     * Row u of the matrix: {@code row[v]} is the weight of (u, v), or
     * {@link #NO_EDGE}. The array is shared, so callers must not modify it.
     */
    public int[] row(int u) {
        return matrix[u];
    }

    /**
     * The length of the path that reaches u at distance {@code distanceU} and
     * then takes the edge in this cell: {@code distanceU + cell}, or
     * {@link ShortestPaths#UNREACHABLE} (infinity) when the cell is
     * {@link #NO_EDGE}. CLRS uses the same convention, w(u, v) = infinity, for
     * missing edges in a weight matrix.
     *
     * <p>It is computed without an if-statement. {@code (cell - 1) >> 31} is
     * all ones when cell is 0 and all zeros when cell is positive, and OR-ing
     * all ones of {@code Long.MAX_VALUE} into a non-negative length gives
     * {@code Long.MAX_VALUE}. With an if-statement the CPU would have to guess
     * "is there an edge here?" for every cell before knowing; on a random
     * graph of density 0.5 that guess is a coin flip, and the wrong guesses
     * make the matrix scan several times slower than on a sparse or complete
     * graph that needs the same work (see {@link MatrixScanControl}).
     */
    public static long distanceThrough(long distanceU, int cell) {
        long noEdgeMask = (cell - 1) >> 31;
        return (distanceU + cell) | (noEdgeMask & Long.MAX_VALUE);
    }
}
