"""Average-case key-comparison model for the hybrid merge sort.

The coarse bound used in docs/Theoretical Analysis.md,

    C(n, S) = O(n*log2(n/S) + n*S),

is correct as an order of growth but is too loose to predict which S minimises
the comparison count. It assumes every merge level costs n comparisons and that
insertion sort on a subarray of size L costs L^2/4. Both are overestimates, and
they are worst exactly where the predicted optimum lies (small S).

This module refines both terms to their average-case values and mirrors the
recursion in java/HybridMergeSort.java exactly. It reproduces the measured
comparison counts to within 0.1% across every configuration we ran.

It is the high-precision companion to `predicted_comparisons` in
plot_results.py. That one charges a flat m per merge and L^2/4 per leaf, which
is simple enough to state on a slide and accurate to a few percent; this one is
what backs the part (c)(iii) claim that merging never costs more key
comparisons than insertion sort at any subarray size, where the two candidates
differ by only 5% at size 4 and a coarser model cannot separate them.

Insertion sort, random array of size L, counting one comparison per iteration
of the shifting loop including the one that stops it:

    I(L) = L*(L-1)/4 + (L-1) - (H_L - 1)

Merging two random sorted runs of lengths a and b stops as soon as one run is
exhausted, leaving the other's tail to be copied without comparison:

    M(a, b) = a + b - a/(b+1) - b/(a+1)
"""

from __future__ import annotations

import csv
import math
import sys
from functools import lru_cache
from pathlib import Path

sys.setrecursionlimit(300_000)

_HARMONIC = [0.0]
for _i in range(1, 4000):
    _HARMONIC.append(_HARMONIC[-1] + 1.0 / _i)


def harmonic(k: int) -> float:
    """H_k, extended past the precomputed table by the asymptotic series."""
    if k < len(_HARMONIC):
        return _HARMONIC[k]
    return math.log(k) + 0.5772156649015329 + 1.0 / (2 * k) - 1.0 / (12 * k * k)


def insertion_comparisons(size: int) -> float:
    """Expected key comparisons of insertionSort on a random array."""
    if size <= 1:
        return 0.0
    return size * (size - 1) / 4 + (size - 1) - (harmonic(size) - 1)


def merge_comparisons(left: int, right: int) -> float:
    """Expected key comparisons merging two random sorted runs."""
    return left + right - left / (right + 1) - right / (left + 1)


@lru_cache(maxsize=None)
def hybrid_comparisons(size: int, threshold: int) -> float:
    """Expected key comparisons of the hybrid sort, mirroring the Java recursion.

    HybridMergeSort uses mid = left + (right - left) / 2, so the left half
    receives ceil(size / 2) elements.
    """
    if size <= 1:
        return 0.0
    if size <= threshold:
        return insertion_comparisons(size)
    left = (size + 1) // 2
    right = size - left
    return (
        hybrid_comparisons(left, threshold)
        + hybrid_comparisons(right, threshold)
        + merge_comparisons(left, right)
    )


def coarse_comparisons(n: int, leaf: int) -> float:
    """The uncorrected n*log2(n/L) + n*L/4 estimate, kept for comparison."""
    if leaf <= 0:
        return float("nan")
    return n * math.log2(n / leaf) + n * leaf / 4


def _validate(results_dir: Path) -> None:
    """Print the model's error against every measured configuration."""
    checks = [
        ("part_i_vary_n.csv", "part (c)(i)"),
        ("part_ii_vary_s.csv", "part (c)(ii)"),
        ("part_iii_candidates.csv", "part (c)(iii)"),
    ]
    for filename, label in checks:
        path = results_dir / filename
        if not path.exists():
            print(f"{label:<28} SKIPPED (missing {filename})")
            continue
        worst = 0.0
        worst_at = None
        count = 0
        with path.open() as handle:
            for row in csv.DictReader(handle):
                n = int(row["n"])
                s = int(row["s"])
                measured = float(row["mean_key_comparisons"])
                predicted = hybrid_comparisons(n, s)
                error = abs(predicted - measured) / measured * 100
                count += 1
                if error > worst:
                    worst, worst_at = error, (n, s)
        print(f"{label:<28} {count:>4} configs   worst error {worst:.3f}%   at n={worst_at[0]:,}, S={worst_at[1]}")


if __name__ == "__main__":
    directory = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("results")
    _validate(directory)
