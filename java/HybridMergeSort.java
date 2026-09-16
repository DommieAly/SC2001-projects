class HybridMergeSort {
    private long comparisonCount;

    public int[] sortArray(int[] nums, int limit) {
        if (nums == null) {
            throw new IllegalArgumentException("Input array cannot be null.");
        }
        if (limit <= 0) {
            throw new IllegalArgumentException("S must be positive.");
        }

        comparisonCount = 0;
        mergeSort(nums, 0, nums.length - 1, limit);
        return nums;
    }

    public long getComparisonCount() {
        return comparisonCount;
    }

    private void mergeSort(int[] nums, int left, int right, int limit) {
        if (left >= right) {
            return;
        }
        if (right - left + 1 <= limit) {
            insertionSort(nums, left, right);
            return;
        }
        int mid = left + (right - left) / 2;
        mergeSort(nums, left, mid, limit);
        mergeSort(nums, mid + 1, right, limit);
        merge(nums, left, mid, right);
    }

    private void insertionSort(int[] nums, int left, int right) {
        for (int i = left + 1; i <= right; i++) {
            int key = nums[i];
            int j = i - 1;

            while (j >= left) {
                incrementComparisonCount();
                if (nums[j] <= key) {
                    break;
                }
                nums[j + 1] = nums[j];
                j--;
            }
            nums[j + 1] = key;
        }
    }

    private void merge(int[] nums, int left, int mid, int right) {
        int[] temp = new int[right - left + 1];
        int i = left;
        int j = mid + 1;
        int k = 0;
        while (i <= mid && j <= right) {
            incrementComparisonCount();
            if (nums[i] <= nums[j]) {
                temp[k++] = nums[i++];
            } else {
                temp[k++] = nums[j++];
            }
        }
        while (i <= mid) {
            temp[k++] = nums[i++];
        }
        while (j <= right) {
            temp[k++] = nums[j++];
        }
        for (int x = 0; x < temp.length; x++) {
            nums[left + x] = temp[x];
        }
    }

    private void incrementComparisonCount() {
        comparisonCount++;
    }
}
