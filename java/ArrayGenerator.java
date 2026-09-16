import java.util.Random;

public class ArrayGenerator {
    public static int[] generateRandomArray(int n, int x) {
        return generateRandomArray(n, x, new Random());
    }

    public static int[] generateRandomArray(int n, int x, long seed) {
        return generateRandomArray(n, x, new Random(seed));
    }

    private static int[] generateRandomArray(int n, int x, Random random) {
        if (n < 0) {
            throw new IllegalArgumentException("Array size cannot be negative.");
        }
        if (x <= 0) {
            throw new IllegalArgumentException("Maximum value must be positive.");
        }

        int[] arr = new int[n];
        for (int i = 0; i < n; i++) {
            arr[i] = random.nextInt(x) + 1;
        }
        return arr;
    }
}
