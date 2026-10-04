import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** The distances and shortest-path tree produced by one run of Dijkstra's algorithm. */
public final class ShortestPaths {
    public static final long UNREACHABLE = Long.MAX_VALUE;
    public static final int NO_PREDECESSOR = -1;

    private final int source;
    private final long[] distance;
    private final int[] predecessor;

    public ShortestPaths(int source, long[] distance, int[] predecessor) {
        this.source = source;
        this.distance = distance;
        this.predecessor = predecessor;
    }

    public int source() {
        return source;
    }

    public int vertexCount() {
        return distance.length;
    }

    public long distanceTo(int v) {
        return distance[v];
    }

    public int predecessorOf(int v) {
        return predecessor[v];
    }

    /** The vertices on a shortest path from the source to v, or an empty list if v is unreachable. */
    public List<Integer> pathTo(int v) {
        List<Integer> path = new ArrayList<>();
        if (distance[v] == UNREACHABLE) {
            return path;
        }
        for (int current = v; current != NO_PREDECESSOR; current = predecessor[current]) {
            path.add(current);
        }
        Collections.reverse(path);
        return path;
    }

    /** True when both runs found the same distance to every vertex. Predecessors may differ on ties. */
    public boolean sameDistances(ShortestPaths other) {
        if (other.distance.length != distance.length) {
            return false;
        }
        for (int v = 0; v < distance.length; v++) {
            if (distance[v] != other.distance[v]) {
                return false;
            }
        }
        return true;
    }
}
