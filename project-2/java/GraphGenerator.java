import java.util.Arrays;
import java.util.SplittableRandom;

/**
 * Random directed graphs with an exact number of vertices and edges, in three
 * weightings that share the same edges.
 *
 * <p>Every graph contains a random Hamiltonian cycle, so every vertex can reach
 * every other one. This keeps the comparison fair: the heap version of
 * Dijkstra's algorithm only touches vertices reachable from the source, while
 * the array version scans all of them, so an unreachable region would favour
 * the heap for a reason unrelated to the data structures.
 *
 * <p>The edges are spread evenly: every vertex gets out-degree floor(E/V) or
 * ceil(E/V), its targets are chosen uniformly at random without repetition, and
 * each adjacency list is sorted by target, so it visits neighbours in the same
 * order as a scan of the matrix row.
 *
 * <ul>
 *   <li>{@link #uniformGraph} gives every edge weight 1. Exactly V - 1
 *       relaxations then lower a key, the fewest possible: the best case.</li>
 *   <li>{@link #randomGraph} draws every weight uniformly from [1, maxWeight].
 *       Few relaxations then lower a key: at least V - 1, and on average at
 *       most about V * H(E/V), where H(k) = 1 + 1/2 + ... + 1/k.</li>
 *   <li>{@link #worstCaseGraph} keeps the same edges and picks weights that make
 *       every relaxation of an edge to a later vertex on the cycle (about half
 *       of all edges) lower a key below every other key except possibly the
 *       next cycle vertex's, so each such decreaseKey in the heap climbs to
 *       the root or to one level below it. This is the input behind the
 *       O(E log V) term of part (b).</li>
 * </ul>
 */
public final class GraphGenerator {
    /** Separates the weight stream from the edge stream, so every weighting gets identical edges. */
    private static final long WEIGHT_STREAM = 0x5DEECE66DL;

    /**
     * A row with fewer than V / SORT_LIMIT targets is sorted; a longer row is
     * collected by one sweep over all V vertices, which is then cheaper than
     * sorting. Both give the same sorted list.
     */
    private static final int SORT_LIMIT = 16;

    private GraphGenerator() {
    }

    /** The edges of a random graph, plus its Hamiltonian cycle read from vertex 0. */
    private record Structure(int[][] neighbours, int[] cycleFromZero) {
    }

    public static AdjacencyListGraph randomGraph(
            int vertexCount, long edgeCount, int maxWeight, long seed) {
        if (maxWeight < 1) {
            throw new IllegalArgumentException("Maximum weight must be positive.");
        }
        Structure structure = randomStructure(vertexCount, edgeCount, seed);
        SplittableRandom random = new SplittableRandom(seed ^ WEIGHT_STREAM);

        int[][] weights = new int[vertexCount][];
        for (int u = 0; u < vertexCount; u++) {
            weights[u] = new int[structure.neighbours()[u].length];
            for (int i = 0; i < weights[u].length; i++) {
                weights[u][i] = 1 + random.nextInt(maxWeight);
            }
        }
        return new AdjacencyListGraph(structure.neighbours(), weights);
    }

    /**
     * The edges of {@code randomGraph(vertexCount, edgeCount, *, seed)} with
     * every weight 1, which makes vertex 0 a best-case source: exactly V - 1
     * relaxations lower a key.
     *
     * <p>Every vertex other than the source is reachable and starts with key
     * infinity, so its key is lowered at least once: no weighting of these
     * edges can do with fewer. With equal
     * weights the first relaxation that reaches a vertex v already sets its
     * final distance. Vertices are extracted in non-decreasing order of
     * distance, so the first in-neighbour u of v to be extracted has the
     * smallest distance of all v's in-neighbours, and d(v) = d(u) + 1. No
     * later relaxation of an edge into v can be shorter, so v gets exactly
     * one decreaseKey.
     */
    public static AdjacencyListGraph uniformGraph(int vertexCount, long edgeCount, long seed) {
        return randomGraph(vertexCount, edgeCount, 1, seed);
    }

    /**
     * The edges of {@code randomGraph(vertexCount, edgeCount, *, seed)} with
     * weights that make vertex 0 a worst-case source for the heap.
     *
     * <p>Let x0 = 0, x1, x2, ... be the vertices in cycle order. Cycle edges
     * get weight 1, so d(x_i) = i and Dijkstra's algorithm extracts the
     * vertices in exactly this order. An edge from x_i forward to a later
     * x_k (other than the cycle edge) gets weight {@code gap[i] + r}, where r
     * counts down along x_i's adjacency list, from x_i's number of such edges
     * to 1. Two facts follow:
     * <ol>
     *   <li>The edges from x_i are relaxed in list order, and their keys
     *       d(x_i) + gap[i] + r fall with every edge, so each new key is
     *       smaller than the one set before it.</li>
     *   <li>{@code gap[i - 1] = gap[i] + (forward edges of x_i) + 2}, so every
     *       key set while processing x_i is smaller than every key set
     *       earlier.</li>
     * </ol>
     * So every relaxation of a forward edge lowers a key, and the new key is
     * smaller than every other key in the queue except possibly x_{i+1}'s:
     * in a heap it climbs to the root, or to just below x_{i+1}. Edges back to
     * earlier vertices get weight 1 and never lower a key, because their
     * target is already finished.
     *
     * <p>The largest weight is F + 2V - 2, where F &lt;= E - V counts the
     * forward edges other than cycle edges, so it is below E + V and the check
     * on E + 3V below is enough.
     */
    public static AdjacencyListGraph worstCaseGraph(int vertexCount, long edgeCount, long seed) {
        if (edgeCount + 3L * vertexCount > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Graph is too large for worst-case weights.");
        }
        Structure structure = randomStructure(vertexCount, edgeCount, seed);
        int[][] neighbours = structure.neighbours();
        int[] cycle = structure.cycleFromZero();
        int[] position = new int[vertexCount];
        for (int i = 0; i < vertexCount; i++) {
            position[cycle[i]] = i;
        }

        // gap[i] is the base weight of x_i's forward edges, built from the last vertex back.
        long[] gap = new long[vertexCount];
        for (int i = vertexCount - 1; i > 0; i--) {
            gap[i - 1] = gap[i] + forwardEdges(cycle[i], neighbours, position) + 2;
        }

        int[][] weights = new int[vertexCount][];
        for (int u = 0; u < vertexCount; u++) {
            int i = position[u];
            int remaining = forwardEdges(u, neighbours, position);
            weights[u] = new int[neighbours[u].length];
            for (int index = 0; index < neighbours[u].length; index++) {
                int target = position[neighbours[u][index]];
                if (target == i + 1 || target < i) {
                    weights[u][index] = 1;
                } else {
                    weights[u][index] = (int) (gap[i] + remaining);
                    remaining--;
                }
            }
        }
        return new AdjacencyListGraph(neighbours, weights);
    }

    /** The number of edges in a complete directed graph without self-loops. */
    public static long maxEdges(int vertexCount) {
        return (long) vertexCount * (vertexCount - 1);
    }

    /** Edges from u to a later vertex in cycle order, not counting u's cycle edge. */
    private static int forwardEdges(int u, int[][] neighbours, int[] position) {
        int count = 0;
        for (int v : neighbours[u]) {
            if (position[v] > position[u] + 1) {
                count++;
            }
        }
        return count;
    }

    private static Structure randomStructure(int vertexCount, long edgeCount, long seed) {
        if (vertexCount < 2) {
            throw new IllegalArgumentException("A graph needs at least two vertices.");
        }
        if (edgeCount < vertexCount || edgeCount > maxEdges(vertexCount)) {
            throw new IllegalArgumentException("Edge count must be between V and V(V-1).");
        }

        SplittableRandom random = new SplittableRandom(seed);
        int[] order = randomPermutation(vertexCount, random);
        int[] successor = new int[vertexCount];
        int[] outDegree = new int[vertexCount];
        int baseDegree = (int) (edgeCount / vertexCount);
        int verticesWithExtraEdge = (int) (edgeCount % vertexCount);

        for (int i = 0; i < vertexCount; i++) {
            successor[order[i]] = order[(i + 1) % vertexCount];
            outDegree[order[i]] = baseDegree + (i < verticesWithExtraEdge ? 1 : 0);
        }

        int[][] neighbours = new int[vertexCount][];
        int[] mark = new int[vertexCount];
        for (int u = 0; u < vertexCount; u++) {
            neighbours[u] = chooseTargets(
                    u, successor[u], outDegree[u], vertexCount, mark, random);
        }

        int[] cycleFromZero = new int[vertexCount];
        int vertex = 0;
        for (int i = 0; i < vertexCount; i++) {
            cycleFromZero[i] = vertex;
            vertex = successor[vertex];
        }
        return new Structure(neighbours, cycleFromZero);
    }

    /**
     * Picks {@code degree} distinct targets for u, always including its cycle
     * successor and never u itself, and returns them in increasing order.
     *
     * <p>{@code mark[v] == u + 1} records that v is already decided for row u,
     * so the array never needs clearing between rows. When at most half of the
     * candidates are wanted, they are drawn at random and repeats are redrawn,
     * so each draw succeeds with probability at least one half. Otherwise the
     * vertices to leave out are drawn instead, with the same guarantee.
     */
    private static int[] chooseTargets(
            int u,
            int successor,
            int degree,
            int vertexCount,
            int[] mark,
            SplittableRandom random) {
        int stamp = u + 1;
        int candidates = vertexCount - 2;
        int wanted = degree - 1;
        int[] targets = new int[degree];
        mark[u] = stamp;
        mark[successor] = stamp;
        targets[0] = successor;

        if (wanted <= candidates / 2) {
            for (int count = 1; count < degree; ) {
                int v = random.nextInt(vertexCount);
                if (mark[v] != stamp) {
                    mark[v] = stamp;
                    targets[count++] = v;
                }
            }
            if ((long) degree * SORT_LIMIT < vertexCount) {
                Arrays.sort(targets);
                return targets;
            }
            int count = 0;
            for (int v = 0; v < vertexCount; v++) {
                if (v != u && mark[v] == stamp) {
                    targets[count++] = v;
                }
            }
            return targets;
        }

        int excluded = candidates - wanted;
        for (int drawn = 0; drawn < excluded; ) {
            int v = random.nextInt(vertexCount);
            if (mark[v] != stamp) {
                mark[v] = stamp;
                drawn++;
            }
        }
        int count = 0;
        for (int v = 0; v < vertexCount; v++) {
            if (v != u && (v == successor || mark[v] != stamp)) {
                targets[count++] = v;
            }
        }
        return targets;
    }

    private static int[] randomPermutation(int size, SplittableRandom random) {
        int[] permutation = new int[size];
        for (int i = 0; i < size; i++) {
            permutation[i] = i;
        }
        for (int i = size - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            int swap = permutation[i];
            permutation[i] = permutation[j];
            permutation[j] = swap;
        }
        return permutation;
    }
}
