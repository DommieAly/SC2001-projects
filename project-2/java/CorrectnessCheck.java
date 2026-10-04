import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;

/**
 * Checks every Dijkstra implementation before any timing is trusted.
 *
 * <ol>
 *   <li>The worked example from CLRS (Figure 22.6 in the 4th edition), whose
 *       distances are known.</li>
 *   <li>Random graphs, including ones where some vertices are unreachable,
 *       compared against Bellman-Ford, which shares no code with Dijkstra.</li>
 *   <li>Graphs from {@link GraphGenerator} at the smallest and largest edge
 *       counts it allows, checked with {@link ShortestPathVerifier}.</li>
 *   <li>The worst-case weights: same edges as the random weights, and every
 *       relaxation of an edge to a later vertex lowers a key.</li>
 * </ol>
 */
public final class CorrectnessCheck {
    private CorrectnessCheck() {
    }

    public static void main(String[] args) {
        checkTextbookExample();
        checkAgainstBellmanFord();
        checkGeneratedGraphs();
        checkWorstCaseWeights();
        System.out.println("All correctness checks passed.");
    }

    private static void checkTextbookExample() {
        // Vertices s, t, x, y, z are 0..4.
        int[][] edges = {
            {0, 1, 10}, {0, 3, 5}, {1, 2, 1}, {1, 3, 2}, {2, 4, 4},
            {3, 1, 3}, {3, 2, 9}, {3, 4, 2}, {4, 0, 7}, {4, 2, 6},
        };
        AdjacencyListGraph graph = fromEdges(5, edges);
        long[] expected = {0, 8, 9, 5, 7};

        for (Map.Entry<String, ShortestPaths> result : runAll(graph, 0).entrySet()) {
            for (int v = 0; v < expected.length; v++) {
                if (result.getValue().distanceTo(v) != expected[v]) {
                    throw new IllegalStateException(
                            result.getKey() + ": wrong distance to vertex " + v
                                    + " in the textbook example.");
                }
            }
        }
        ShortestPaths paths = new DijkstraListHeap().run(graph, 0);
        if (!paths.pathTo(2).equals(List.of(0, 3, 1, 2))) {
            throw new IllegalStateException("Textbook example: wrong path to x.");
        }
        System.out.println("  textbook example: distances " + Arrays.toString(expected)
                + ", path s -> x = " + paths.pathTo(2));
    }

    private static void checkAgainstBellmanFord() {
        SplittableRandom random = new SplittableRandom(20261004L);
        int graphs = 0;

        for (int vertexCount : new int[] {1, 2, 3, 5, 10, 30, 100}) {
            for (double edgeProbability : new double[] {0.0, 0.05, 0.2, 0.5, 1.0}) {
                for (int maxWeight : new int[] {1, 3, 1_000}) {
                    for (int repeat = 0; repeat < 5; repeat++) {
                        AdjacencyListGraph graph = randomGraph(
                                vertexCount, edgeProbability, maxWeight, random);
                        int source = random.nextInt(vertexCount);
                        long[] expected = bellmanFord(graph, source);

                        for (Map.Entry<String, ShortestPaths> result
                                : runAll(graph, source).entrySet()) {
                            ShortestPathVerifier.verify(graph, result.getValue());
                            for (int v = 0; v < vertexCount; v++) {
                                if (result.getValue().distanceTo(v) != expected[v]) {
                                    throw new IllegalStateException(
                                            result.getKey() + " disagrees with Bellman-Ford at V="
                                                    + vertexCount + ", p=" + edgeProbability
                                                    + ", maxWeight=" + maxWeight
                                                    + ", source=" + source + ", vertex " + v);
                                }
                            }
                        }
                        graphs++;
                    }
                }
            }
        }
        System.out.println("  " + graphs + " random graphs agree with Bellman-Ford");
    }

    private static void checkGeneratedGraphs() {
        int graphs = 0;
        for (int vertexCount : new int[] {2, 3, 4, 17, 64, 300}) {
            for (long edgeCount : edgeCountsToCheck(vertexCount)) {
                AdjacencyListGraph graph =
                        GraphGenerator.randomGraph(vertexCount, edgeCount, 1_000, edgeCount);
                if (graph.edgeCount() != edgeCount
                        || AdjacencyMatrixGraph.from(graph).edgeCount() != edgeCount) {
                    throw new IllegalStateException("Generator produced the wrong number of edges.");
                }
                checkGeneratedStructure(graph);

                Map<String, ShortestPaths> results = runAll(graph, 0);
                ShortestPaths first = results.values().iterator().next();
                for (Map.Entry<String, ShortestPaths> result : results.entrySet()) {
                    ShortestPathVerifier.verify(graph, result.getValue());
                    if (!result.getValue().sameDistances(first)) {
                        throw new IllegalStateException(
                                result.getKey() + " disagrees with the others at V="
                                        + vertexCount + ", E=" + edgeCount);
                    }
                    for (int v = 0; v < vertexCount; v++) {
                        if (result.getValue().distanceTo(v) == ShortestPaths.UNREACHABLE) {
                            throw new IllegalStateException("Generated graph is not strongly connected.");
                        }
                    }
                }
                graphs++;
            }
        }
        System.out.println("  " + graphs + " generated graphs pass ShortestPathVerifier");
    }

    /**
     * With worst-case weights, d(x_i) = i along the cycle, so the distances are
     * exactly 0..V-1, and every edge (u, v) with d[v] &gt; d[u] must lower a key
     * in every implementation.
     */
    private static void checkWorstCaseWeights() {
        int graphs = 0;
        for (int vertexCount : new int[] {2, 3, 4, 17, 64, 300}) {
            for (long edgeCount : edgeCountsToCheck(vertexCount)) {
                AdjacencyListGraph worst = GraphGenerator.worstCaseGraph(vertexCount, edgeCount, edgeCount);
                AdjacencyListGraph random =
                        GraphGenerator.randomGraph(vertexCount, edgeCount, 1_000, edgeCount);
                for (int u = 0; u < vertexCount; u++) {
                    if (!Arrays.equals(worst.neighboursOf(u), random.neighboursOf(u))) {
                        throw new IllegalStateException("Worst-case weights changed the edges.");
                    }
                }

                ShortestPaths paths = new DijkstraListHeap().run(worst, 0);
                ShortestPathVerifier.verify(worst, paths);
                boolean[] seen = new boolean[vertexCount];
                long forwardEdges = 0;
                for (int u = 0; u < vertexCount; u++) {
                    seen[(int) paths.distanceTo(u)] = true;
                    for (int v : worst.neighboursOf(u)) {
                        if (paths.distanceTo(v) > paths.distanceTo(u)) {
                            forwardEdges++;
                        }
                    }
                }
                for (boolean distanceSeen : seen) {
                    if (!distanceSeen) {
                        throw new IllegalStateException("Worst-case distances are not 0..V-1.");
                    }
                }

                AdjacencyMatrixGraph matrix = AdjacencyMatrixGraph.from(worst);
                DijkstraMatrixArray a = new DijkstraMatrixArray();
                DijkstraListHeap b = new DijkstraListHeap();
                DijkstraMatrixHeap matrixHeap = new DijkstraMatrixHeap();
                DijkstraListArray listArray = new DijkstraListArray();
                a.run(matrix, 0);
                b.run(worst, 0);
                matrixHeap.run(matrix, 0);
                listArray.run(worst, 0);
                long[] decreaseKeys = {
                    a.getDecreaseKeyCount(), b.getDecreaseKeyCount(),
                    matrixHeap.getDecreaseKeyCount(), listArray.getDecreaseKeyCount(),
                };
                for (long count : decreaseKeys) {
                    if (count != forwardEdges) {
                        throw new IllegalStateException(
                                "Worst-case weights: " + count + " decrease-keys, expected "
                                        + forwardEdges + " at V=" + vertexCount + ", E=" + edgeCount);
                    }
                }
                graphs++;
            }
        }
        System.out.println("  " + graphs + " worst-case graphs lower a key on every forward edge");
    }

    private static long[] edgeCountsToCheck(int vertexCount) {
        long maxEdges = GraphGenerator.maxEdges(vertexCount);
        return Arrays.stream(new long[] {
                    vertexCount, Math.min(maxEdges, 3L * vertexCount),
                    maxEdges / 2, maxEdges - 1, maxEdges,
                })
                .filter(edgeCount -> edgeCount >= vertexCount)
                .distinct()
                .toArray();
    }

    /** Lists must be sorted, without self-loops, and without repeated targets. */
    private static void checkGeneratedStructure(AdjacencyListGraph graph) {
        for (int u = 0; u < graph.vertexCount(); u++) {
            int[] neighbours = graph.neighboursOf(u);
            for (int i = 0; i < neighbours.length; i++) {
                if (neighbours[i] == u) {
                    throw new IllegalStateException("Generated graph has a self-loop.");
                }
                if (i > 0 && neighbours[i - 1] >= neighbours[i]) {
                    throw new IllegalStateException("Adjacency list is unsorted or repeats a target.");
                }
            }
        }
    }

    /** Every implementation, keyed by its name, run from the same source. */
    private static Map<String, ShortestPaths> runAll(AdjacencyListGraph graph, int source) {
        AdjacencyMatrixGraph matrix = AdjacencyMatrixGraph.from(graph);
        Map<String, ShortestPaths> results = new LinkedHashMap<>();
        results.put("Matrix + array (a)", new DijkstraMatrixArray().run(matrix, source));
        results.put("Lists + heap (b)", new DijkstraListHeap().run(graph, source));
        results.put("Matrix + heap", new DijkstraMatrixHeap().run(matrix, source));
        results.put("Lists + array", new DijkstraListArray().run(graph, source));
        results.put("Matrix scan control", MatrixScanControl.run(matrix, source));
        return results;
    }

    /** O(VE) shortest paths that relaxes every edge V - 1 times. */
    private static long[] bellmanFord(AdjacencyListGraph graph, int source) {
        int vertexCount = graph.vertexCount();
        long[] distance = new long[vertexCount];
        Arrays.fill(distance, ShortestPaths.UNREACHABLE);
        distance[source] = 0;

        for (int round = 1; round < vertexCount; round++) {
            for (int u = 0; u < vertexCount; u++) {
                if (distance[u] == ShortestPaths.UNREACHABLE) {
                    continue;
                }
                int[] neighbours = graph.neighboursOf(u);
                int[] weights = graph.weightsOf(u);
                for (int i = 0; i < neighbours.length; i++) {
                    distance[neighbours[i]] =
                            Math.min(distance[neighbours[i]], distance[u] + weights[i]);
                }
            }
        }
        return distance;
    }

    /** Each ordered pair (u, v), u != v, is an edge with the given probability. */
    private static AdjacencyListGraph randomGraph(
            int vertexCount, double edgeProbability, int maxWeight, SplittableRandom random) {
        List<int[]> edges = new ArrayList<>();
        for (int u = 0; u < vertexCount; u++) {
            for (int v = 0; v < vertexCount; v++) {
                if (u != v && random.nextDouble() < edgeProbability) {
                    edges.add(new int[] {u, v, 1 + random.nextInt(maxWeight)});
                }
            }
        }
        return fromEdges(vertexCount, edges.toArray(new int[0][]));
    }

    private static AdjacencyListGraph fromEdges(int vertexCount, int[][] edges) {
        int[] outDegree = new int[vertexCount];
        for (int[] edge : edges) {
            outDegree[edge[0]]++;
        }
        int[][] neighbours = new int[vertexCount][];
        int[][] weights = new int[vertexCount][];
        for (int u = 0; u < vertexCount; u++) {
            neighbours[u] = new int[outDegree[u]];
            weights[u] = new int[outDegree[u]];
        }
        int[] filled = new int[vertexCount];
        for (int[] edge : edges) {
            int u = edge[0];
            neighbours[u][filled[u]] = edge[1];
            weights[u][filled[u]] = edge[2];
            filled[u]++;
        }
        return new AdjacencyListGraph(neighbours, weights);
    }
}
