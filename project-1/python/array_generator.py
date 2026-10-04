"""Generate part (b) datasets and optionally time the Python hybrid sort."""

import argparse
import random
from time import process_time


DEFAULT_SIZES = (1_000, 5_000, 10_000, 50_000, 100_000,
                 500_000, 1_000_000, 5_000_000, 10_000_000)


def generate_random_array(n, x, seed=None):
    if n < 0:
        raise ValueError("Array size cannot be negative.")
    if x <= 0:
        raise ValueError("Maximum value must be positive.")
    rng = random.Random(seed)
    return [rng.randint(1, x) for _ in range(n)]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--sizes", nargs="+", type=int, default=DEFAULT_SIZES,
                        help="Input sizes (default: 1,000 through 10 million).")
    parser.add_argument("--max-value", type=int, default=1_000_000,
                        help="Inclusive upper bound x (default: 1000000).")
    parser.add_argument("--seed", type=int, default=20260910)
    parser.add_argument("--sort", action="store_true",
                        help="Also run and time pythonsyn.hybridsort.")
    parser.add_argument("--threshold", type=int, default=16,
                        help="Insertion sort threshold S (default: 16).")
    args = parser.parse_args()
    if any(n < 0 for n in args.sizes):
        parser.error("Array sizes cannot be negative.")
    if args.max_value <= 0 or args.threshold <= 0:
        parser.error("Maximum value and threshold must be positive.")

    if args.sort:
        from pythonsyn import hybridsort

    for n in sorted(args.sizes):
        # Keep only one dataset at a time, even for the largest input sizes.
        arr = generate_random_array(n, args.max_value, args.seed)
        print(f"n={n:,}, x={args.max_value:,}, sample={arr[:10]}", flush=True)
        if args.sort:
            start = process_time()
            result = hybridsort(arr, args.threshold)
            elapsed = process_time() - start
            # Validation is outside the timed region and avoids a sorted copy.
            ordered = len(result) == n and all(
                result[i - 1] <= result[i] for i in range(1, n)
            )
            if not ordered:
                raise RuntimeError(f"Sorting failed for n={n}.")
            print(f"  S={args.threshold}, CPU time={elapsed:.6f}s, ordered=True",
                  flush=True)
            del result
        del arr


if __name__ == "__main__":
    main()
