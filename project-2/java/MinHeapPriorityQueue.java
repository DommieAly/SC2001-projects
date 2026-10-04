/**
 * Minimum priority queue stored as a binary min-heap (part b), in the 0-based
 * array layout of lecture 3: node i has children 2i+1 and 2i+2 and parent
 * (i-1)/2, and every node's key is at most its children's keys.
 *
 * <p>Dijkstra's algorithm lowers the key of a vertex that is already in the
 * heap, so the heap also records which node holds each vertex. decreaseKey can
 * then find the vertex in O(1) and move it up the way Enqueue does in the
 * lecture, in O(log V).
 *
 * <p>Both sift operations carry the moving entry along and write it once at
 * its final node, instead of exchanging it at every level. The comparisons are
 * exactly those of the lecture's exchange-based version.
 */
public final class MinHeapPriorityQueue {
    private final int[] vertices;
    private final long[] keys;
    private final int[] position;
    private int size;
    private long comparisonCount;

    /** Puts every vertex v in the heap with key {@code initialKeys[v]} using Build-Min-Heap, in O(V). */
    public MinHeapPriorityQueue(long[] initialKeys) {
        int vertexCount = initialKeys.length;
        vertices = new int[vertexCount];
        keys = new long[vertexCount];
        position = new int[vertexCount];

        for (int v = 0; v < vertexCount; v++) {
            vertices[v] = v;
            keys[v] = initialKeys[v];
            position[v] = v;
        }
        size = vertexCount;
        for (int node = size / 2 - 1; node >= 0; node--) {
            minHeapify(node);
        }
    }

    public boolean isEmpty() {
        return size == 0;
    }

    /** Dequeue from lecture 3: replace the root by the last entry, then Min-Heapify the root. */
    public int extractMin() {
        if (size == 0) {
            throw new IllegalStateException("The priority queue is empty.");
        }

        int minimum = vertices[0];
        position[minimum] = -1;
        size--;
        if (size > 0) {
            place(0, vertices[size], keys[size]);
            minHeapify(0);
        }
        return minimum;
    }

    /**
     * Lowers the key of v, which must still be in the heap, then moves it up
     * as in Enqueue from lecture 3: past every parent with a larger key.
     */
    public void decreaseKey(int v, long key) {
        int node = position[v];
        long comparisons = 0;

        while (node > 0) {
            int parent = (node - 1) / 2;
            comparisons++;
            if (keys[parent] <= key) {
                break;
            }
            place(node, vertices[parent], keys[parent]);
            node = parent;
        }
        place(node, v, key);
        comparisonCount += comparisons;
    }

    public long getComparisonCount() {
        return comparisonCount;
    }

    /** Min-Heapify from lecture 3, written as a loop instead of a recursive call. */
    private void minHeapify(int node) {
        int vertex = vertices[node];
        long key = keys[node];
        long comparisons = 0;

        while (true) {
            int left = 2 * node + 1;
            if (left >= size) {
                break;
            }
            int right = left + 1;
            int smallest = left;
            if (right < size) {
                comparisons++;
                if (keys[right] < keys[left]) {
                    smallest = right;
                }
            }
            comparisons++;
            if (keys[smallest] >= key) {
                break;
            }
            place(node, vertices[smallest], keys[smallest]);
            node = smallest;
        }
        place(node, vertex, key);
        comparisonCount += comparisons;
    }

    private void place(int node, int vertex, long key) {
        vertices[node] = vertex;
        keys[node] = key;
        position[vertex] = node;
    }
}
