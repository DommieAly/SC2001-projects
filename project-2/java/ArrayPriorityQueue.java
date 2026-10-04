/**
 * Minimum priority queue stored in an unsorted array (part a).
 *
 * <p>Entries sit in no particular order, so finding the minimum means looking
 * at every entry: extractMin costs Theta(|Q|). In exchange, decreaseKey only
 * overwrites one key, found through the position index in O(1), and removing
 * the minimum fills its slot with the last entry, also O(1).
 */
public final class ArrayPriorityQueue {
    private final int[] vertices;
    private final long[] keys;
    private final int[] position;
    private int size;
    private long comparisonCount;

    /** Puts every vertex v in the queue with key {@code initialKeys[v]}, in O(V). */
    public ArrayPriorityQueue(long[] initialKeys) {
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
    }

    public boolean isEmpty() {
        return size == 0;
    }

    /** Removes and returns the vertex with the smallest key by scanning every slot. */
    public int extractMin() {
        if (size == 0) {
            throw new IllegalStateException("The priority queue is empty.");
        }

        int smallest = 0;
        for (int slot = 1; slot < size; slot++) {
            if (keys[slot] < keys[smallest]) {
                smallest = slot;
            }
        }
        comparisonCount += size - 1;

        int minimum = vertices[smallest];
        size--;
        vertices[smallest] = vertices[size];
        keys[smallest] = keys[size];
        position[vertices[smallest]] = smallest;
        position[minimum] = -1;
        return minimum;
    }

    /** Lowers the key of v, which must still be in the queue, in O(1). */
    public void decreaseKey(int v, long key) {
        keys[position[v]] = key;
    }

    public long getComparisonCount() {
        return comparisonCount;
    }
}
