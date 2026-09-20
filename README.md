# Merge Sort Experiments

This project evaluates a Hybrid Merge Sort that switches to Insertion Sort
when a subarray contains at most `S` elements.

## Experimental setup

- Random integers are uniformly generated from 1 to 1,000,000.
- The base seed is `20260910`, so the datasets are reproducible.
- Every configuration is tested 10 times. Algorithms being compared receive
  identical input arrays.
- A key comparison is a comparison between two array values. Array-index and
  loop-control comparisons are excluded.
- Array generation and correctness checking are excluded from measured times.
- Mean key comparisons and median times are reported. Median time is used to
  reduce the effect of occasional timing outliers.

## Part (c)(i): Fixed S, different input sizes

`S` was fixed at 16 and `n` ranged from 1,000 to 10,000,000. The measured key
comparisons closely followed the `n log2(n)` reference curve. Therefore, when
`S` is fixed, the Hybrid Merge Sort has time complexity:

```text
Theta(n log n)
```

![Part (c)(i)](plots/part_i_vary_n.png)

## Part (c)(ii): Fixed input size, different S values

`n` was fixed at 100,000 and `S = 1, 10, 20, ..., 1000` was tested. The
theoretical comparison growth is:

```text
Theta(n log(n / S) + nS)
```

The merge term decreases as `S` increases, but the Insertion Sort term grows.
For these random datasets, the Insertion Sort term eventually dominates. The
fewest key comparisons occurred at `S = 1`; the count increased from about
1.54 million at `S = 1` to 20.32 million at `S = 1000`.

The step-shaped empirical curve is expected. Since Merge Sort repeatedly
halves each subarray, several consecutive `S` values can produce the same leaf
subarrays and therefore the same number of comparisons.

![Part (c)(ii)](plots/part_ii_vary_s.png)

## Part (c)(iii): Choosing an optimal S

A threshold only matters through the halving interval it falls in: every `S`
with `ceil(n/2^k) <= S <= floor(n/2^(k-1)) - 1` drives the recursion to the same
depth, so it builds an identical tree and performs an identical number of key
comparisons. Sweeping `S` one integer at a time therefore re-measures the same
computation over and over. The search instead tests one representative per
interval, which covers every distinct behaviour in 12 candidates per input size.

Each run also measures one candidate a second time under a separate label. That
control does the same work as its twin, so the gap between them is this
machine's noise floor. Candidates are only called different when they are
further apart than two standard errors.

| Input size n | Fastest leaf size | Interval of S | Median time | Best S by key comparisons |
|---:|---:|---|---:|---:|
| 10,000 | 40 | `40`..`77` | 0.679 ms | 1 |
| 50,000 | 49 | `49`..`96` | 4.229 ms | 1 |
| 100,000 | 49 | `49`..`96` | 9.064 ms | 1 |
| 500,000 | 31 | `31`..`60` | 52.748 ms | 1 |

The quantity that stays put as `n` grows is the leaf size, not `S`. The
achievable leaf sizes are `n / 2^k`, a different ladder for each `n`, and the
runtime minimum lands on the rung nearest 30 to 60 every time. `S = 50` falls
inside the winning interval for all four input sizes.

The comparison-count optimum is `S = 1` for every input size, that is, plain
merge sort. On key comparisons alone the hybrid can never win: merging is never
more expensive than insertion sort at any subarray size, and ties only at sizes
2 and 3. The hybrid's advantage is entirely in running time, where it avoids a
temporary array allocation and a stack frame per merge.

![Part (c)(iii)](plots/part_iii_optimal_s.png)

## Part (d): Original Merge Sort vs Hybrid Merge Sort

Both algorithms sorted identical datasets containing 10 million integers. The
Hybrid Merge Sort used `S = 60`. Current-thread CPU time was measured, and the
algorithm execution order was alternated between trials to reduce ordering
bias.

| Algorithm | Mean key comparisons | Median CPU time |
|---|---:|---:|
| Original Merge Sort | 220,102,181 | 1482.49 ms |
| Hybrid Merge Sort (`S = 60`) | 281,247,267 | 1287.94 ms |

The Hybrid Merge Sort performed 27.8% more key comparisons but used 13.1% less
CPU time, giving a speedup of approximately 1.15 times. Its advantage comes
from lower recursion and merge overhead on small subarrays, not from reducing
the number of key comparisons.

![Part (d)](plots/part_d_comparison.png)

## Run

```bash
javac -d out java/*.java
java -cp out ExperimentRunner results
python3 python/plot_results.py results
```

Run these commands from the repository root. `ExperimentRunner` takes an
optional second argument naming the parts to run, for example
`java -cp out ExperimentRunner results ciii`, so a single part can be repeated
without overwriting results the report already quotes. CSV data is written to
`results/`.
The generated figures are written to `plots/`.
