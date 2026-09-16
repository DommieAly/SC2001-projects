def merge_sort(arr,S):
    if len(arr) <= 1:
        return arr
    if len(arr)<=S:
        insertionSort(arr)
        return arr

    mid = len(arr) // 2
    left_half = merge_sort(arr[:mid],S)
    right_half = merge_sort(arr[mid:],S)
    return merge(left_half, right_half)

def merge(left, right): 
    sorted_array = []
    i = j = 0

    while i < len(left) and j < len(right):
        if left[i] <= right[j]: 
            sorted_array.append(left[i])
            i += 1
        else:
            sorted_array.append(right[j])
            j += 1

    sorted_array.extend(left[i:])
    sorted_array.extend(right[j:])
    return sorted_array

def insertionSort(arr):
    for i in range(1, len(arr)):
        key = arr[i]
        j = i - 1

        while j >= 0 and key < arr[j]:
            arr[j + 1] = arr[j]
            j -= 1
        arr[j + 1] = key



