# Dijkstra's Algorithm Experiments

This project implements Dijkstra's algorithm in the two settings of the
assignment and compares them:

- **(a)** the graph in an adjacency matrix, the priority queue in an array;
- **(b)** the graph in an array of adjacency lists, the priority queue in a
  minimizing heap.

The derivations behind every complexity claim below are in
[docs/Theoretical Analysis.md](docs/Theoretical%20Analysis.md).

## Code

| File | Role |
|---|---|
| `java/AdjacencyMatrixGraph.java` | (a) graph: `V × V` matrix of weights, `0` means no edge (lecture 4) |
| `java/ArrayPriorityQueue.java` | (a) queue: unsorted array; `extractMin` scans it, `decreaseKey` is `O(1)` |
| `java/DijkstraMatrixArray.java` | **Part (a)** |
| `java/AdjacencyListGraph.java` | (b) graph: for each vertex, an array of neighbours and one of weights (lecture 4) |
| `java/MinHeapPriorityQueue.java` | (b) queue: 0-based binary min-heap with `Build-Min-Heap`, `Min-Heapify` and the `Enqueue` sift-up of lecture 3, plus an index so `decreaseKey` can find a vertex |
| `java/DijkstraListHeap.java` | **Part (b)** |
| `java/DijkstraMatrixHeap.java`, `java/DijkstraListArray.java` | Part (c): the two mixed combinations, to separate the effect of the graph representation from the effect of the queue |
| `java/MatrixScanControl.java` | Part (a) control (appendix): the matrix scan written with a plain `if` on the edge test |
| `java/ShortestPaths.java` | Result of one run: distances and predecessors |
| `java/GraphGenerator.java` | Random graphs with exact `V` and `E`, in three weightings of the same edges: uniform, random and worst case |
| `java/ShortestPathVerifier.java` | Checks any result against the graph in `O(V + E)` |
| `java/CorrectnessCheck.java` | CLRS worked example, 525 random graphs against Bellman-Ford, generator checks, decrease-key counts of the uniform and worst-case weights |
| `java/ExperimentRunner.java`, `java/Metrics.java` | Experiments; CSV output in `results/` |
| `python/plot_results.py` | Figures in `plots/` |

All four Dijkstra classes follow CLRS (4th ed., Section 22.3), with one added
line that stops at the first unreachable vertex. Every edge of the extracted
vertex is relaxed; a relaxation into an already finished vertex simply fails,
because that vertex's distance is already the smallest.

## Experimental setup

- **Terms.** *Density* is `E / V(V − 1)`, the fraction of all possible edges
  that are present. A *forward edge* is an edge to a vertex that comes later
  on the graph's Hamiltonian cycle (see below), counted from the source.
- **Graphs.** Directed, with exactly `E` edges. Every vertex has out-degree
  `⌊E/V⌋` or `⌈E/V⌉`, with targets drawn uniformly at random and without
  repeats. Every graph contains a random cycle through all vertices (a
  Hamiltonian cycle), so all vertices are reachable from the source, vertex 0.
  Without that, (b) would skip the unreachable vertices while (a) still
  scanned them, which would favour (b) for a reason unrelated to the data
  structures. Each adjacency list is sorted by target, the order in which a
  scan of the matrix row meets them.
- **Three weightings of the same edges, one per case.** Only the number of
  relaxations that lower a key (decrease-keys) depends on the weights.
  - *Uniform weights (best case):* every weight is 1. The first relaxation
    that reaches a vertex already sets its final distance, so exactly
    `V − 1` relaxations lower a key, the fewest possible (proof in the theory
    document). `CorrectnessCheck` confirms the count in all four
    implementations.
  - *Random weights (average case):* uniform integers from 1 to 1,000. On
    average at most about `V · H(E/V)` decrease-keys, where
    `H(k) = 1 + 1/2 + ... + 1/k`.
  - *Worst-case weights:* chosen so that every relaxation of a forward edge
    lowers a key below every other key in the queue, except possibly the key
    of the next vertex on the cycle. That is about `E/2` decrease-keys, and
    in the heap every one climbs to the root or to a child of the root. This
    is the input behind the `E log V` term of (b), which random weights
    never reach. `CorrectnessCheck` confirms that the decrease-key count
    equals the number of forward edges.
- **One change at a time.** Parts (a) and (b) each run three experiments:
  - **(i) vary `V`**, with `E` (part a) or `E/V` (part b) fixed, random
    weights;
  - **(ii) vary `E`** at `V = 5,000`, random weights, over 17 edge counts
    from a single cycle (`E = V`) to the complete graph;
  - **(iii) best, average and worst case:** the sweep of (ii) with all three
    weightings.

  (ii) and (iii) share one CSV file per part, and (ii) is its random-weight
  rows. Parts (a) and (b) run on the same graphs in (ii) and (iii), so their
  times can be compared row by row.
- **Seeds.** The base seed is `20261004`. A given `(V, E, trial)` always
  produces the same edges, in every part and in every weighting.
- **Trials.** Each trial of a configuration uses a new graph, so no
  implementation is timed twice in a row on the same graph. Repeating one
  graph lets the CPU learn the run and makes short runs look faster. Each
  configuration gets at least 10 trials; small graphs get more, until 100 ms
  of CPU time has been measured (at most 100 trials).
- **Timing.** Current-thread CPU time of the `run` call alone. Graph
  generation, building the matrix, and verification are not timed. Medians
  are reported. The JVM is warmed up on every implementation first. All runs
  were on an Apple M2 Pro with Java 23 and `-Xmx6g`. Parts (a) and (b) were
  last run on battery power with macOS Low Power Mode on; part (c) comes
  from an earlier run of the same code.
- **Counts.** Machine-independent counts are recorded next to the time:
  - *queue comparisons:* key comparisons inside the priority queue;
  - *relaxations:* edges `(u, v)` tested with `d[u] + w(u, v) < d[v]`
    (exactly `E` when every vertex is reachable);
  - *decrease-keys:* relaxations that found a shorter path;
  - *entries read:* matrix cells for (a), list entries for (b).
- **Verification.** Every timed result is checked by `ShortestPathVerifier`.
  In part (c), all four implementations must also agree on every distance.

## Part (a): adjacency matrix + array priority queue

Theory: `Θ(V²)` for any `E`. Each of the `V` extractions scans the whole
queue (`V(V − 1)/2` comparisons in total), and each extracted vertex reads its
whole matrix row (`V²` cells in total). The `E` relaxations and the
decrease-keys cost `O(1)` each, and `E ≤ V(V − 1)`. So best, average and
worst case are all `Θ(V²)`.

### (i) Varying V at E = 100,000

`V` from 500 to 8,000; `E` fixed at 100,000; random weights. 100,000 edges
fit every `V`: at least `V` for the Hamiltonian cycle, and at most
`V(V − 1) = 249,500` at `V = 500`. Only `V` changes, so the density falls
from 0.40 to 0.0016.

| V | Density | Time | Time ÷ V² | Decrease-keys |
|---:|---:|---:|---:|---:|
| 500 | 0.40 | 0.253 ms | 1.01 ns | 2,385 |
| 1,000 | 0.10 | 0.915 ms | 0.92 ns | 4,148 |
| 2,000 | 0.025 | 3.700 ms | 0.93 ns | 6,965 |
| 4,000 | 0.0063 | 15.08 ms | 0.94 ns | 11,321 |
| 8,000 | 0.0016 | 56.98 ms | 0.89 ns | 17,493 |

- The time grows with slope 1.97 on a log-log plot (least squares over all
  nine `V`; 1.98 without `V = 500`), against 2 for `V²`. Time divided by the
  `V²` cells read stays between 0.89 and 1.01 ns, which includes the
  `V(V − 1)/2` queue comparisons.
- In every row, cells read is exactly `V²`, queue comparisons exactly
  `V(V − 1)/2`, and relaxations exactly `E = 100,000`. The work that depends
  on `E` is the same in every row, so all of the growth comes from `V`.

![Part (a)(i), varying V](plots/part_a_vary_v.png)

### (ii) Varying E at V = 5,000

`E` from 5,000 (a single cycle) to 24,995,000 (the complete graph); `V`
fixed at 5,000; random weights.

| E | Density | Time | Relaxations | Decrease-keys |
|---:|---:|---:|---:|---:|
| 5,000 | 0.0002 | 22.24 ms | 5,000 | 4,999 |
| 1,000,000 | 0.04 | 22.44 ms | 1,000,000 | 23,980 |
| 12,500,000 | 0.5 | 23.18 ms | 12,500,000 | 32,902 |
| 17,500,000 | 0.7 | 23.51 ms | 17,500,000 | 33,462 |
| 24,995,000 | 1.0 | 22.79 ms | 24,995,000 | 34,043 |

- The time stays between 22.24 and 23.51 ms (within 5.7%) while `E` grows
  4,999-fold, as `Θ(V²)` predicts. A least-squares line through the 17
  times rises by 0.03 ns per edge, against 0.86 ns per edge for (b) on the
  same graphs.
- Cells read (25,000,000 = `V²`) and queue comparisons
  (12,497,500 = `V(V − 1)/2`) are the same in every row. Only the
  relaxations (`= E`) and the decrease-keys grow with `E`, and even on the
  complete graph the relaxations stay below `V²` and only 34,043 of them
  lower a key.

![Part (a)(ii), varying E](plots/part_a_vary_e.png)

### (iii) Best, average and worst case at V = 5,000

The sweep of (ii) with uniform, random and worst-case weights.

| E | Density | Time, uniform | Time, random | Time, worst case | Decrease-keys, uniform | Decrease-keys, random | Decrease-keys, worst case |
|---:|---:|---:|---:|---:|---:|---:|---:|
| 5,000 | 0.0002 | 22.48 ms | 22.24 ms | 22.67 ms | 4,999 | 4,999 | 4,999 |
| 1,000,000 | 0.04 | 22.01 ms | 22.44 ms | 27.13 ms | 4,999 | 23,980 | 502,410 |
| 12,500,000 | 0.5 | 22.79 ms | 23.18 ms | 72.45 ms | 4,999 | 32,902 | 6,251,326 |
| 17,500,000 | 0.7 | 22.66 ms | 23.51 ms | 80.47 ms | 4,999 | 33,462 | 8,750,640 |
| 24,995,000 | 1.0 | 22.23 ms | 22.79 ms | 27.80 ms | 4,999 | 34,043 | 12,497,500 |

- **The decrease-keys follow the theory.** Uniform weights give exactly
  4,999 = `V − 1` in every row. Random weights give 4,999 to 34,043, below
  `V · H(E/V)`. Worst-case weights give one per forward edge, about `E/2`.
- **Best and average case take the same time,** within 3.6% at every `E`
  (uniform 21.87-23.17 ms, random 22.24-23.51 ms). Both lower few keys, at
  `O(1)` each, so the `V²` scan decides.
- **The worst case is not flat.** It matches random weights up to
  `E = 100,000` (within 2%), rises to a peak of 80.47 ms at density 0.7,
  3.42 times the random-weight time, and falls back to 27.80 ms on the
  complete graph, although that graph has the most decrease-keys.

**Why the worst case has a bump.** The extra work itself is cheap: on the
complete graph, 12,463,457 more decrease-keys than with random weights add
5.01 ms, 0.40 ns each. At density 0.5 the extra decrease-keys cost 7.9 ns
each. The difference is the relaxation test `if (candidate < d[v])`. The CPU
guesses the outcome of every `if` before it is known, and throws work away
when the guess is wrong (see the [appendix](#appendix-why-the-matrix-scan-has-no-if-statement)).

- In the row of the `i`-th extracted vertex, a cell passes the test when it
  holds an edge (probability `d`, the density) to a vertex that is not yet
  finished (fraction `(V − i)/V`). So the pass rate `p = d(V − i)/V` falls
  from `d` to 0 during the run. When the passes are scattered at random, the
  best guess is wrong in about `min(p, 1 − p)` of the cells. Averaged over
  the run that is `d/2` up to `d = 0.5`, with a peak of 0.29 at
  `d = 1/√2 ≈ 0.71`.
- The extra time over random weights follows this shape up to density 0.8,
  at 7.5-8.8 ns per predicted wrong guess:

  | Density | 0.1 | 0.2 | 0.4 | 0.5 | 0.6 | 0.7 | 0.8 | 0.9 | 1.0 |
  |---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
  | Extra time over random weights | 11.0 ms | 20.8 ms | 39.9 ms | 49.3 ms | 55.9 ms | 57.0 ms | 54.1 ms | 41.9 ms | 5.0 ms |
  | Measured, relative to density 0.5 | 0.22 | 0.42 | 0.81 | 1 | 1.13 | 1.16 | 1.10 | 0.85 | 0.10 |
  | Model `min(p, 1 − p)`, relative to density 0.5 | 0.20 | 0.40 | 0.80 | 1 | 1.13 | 1.17 | 1.15 | 1.09 | 1.00 |

- At densities 0.9 and 1.0 the model predicts too much, because the passes
  are no longer scattered at random. On the complete graph a cell passes
  exactly when its vertex is unfinished, so the pattern of passes in each
  row is the previous row's with one vertex removed. The CPU guesses from
  the recent history of outcomes, so it learns a pattern that repeats, and
  the extra time falls to 5.0 ms. At density 0.5 the passes are a fresh
  random half of the unfinished vertices in every row, which cannot be
  learned.
- This changes the constant, not the growth: each cell adds at most one
  wrong guess, so the worst case is still `Θ(V²)`.

![Part (a)(iii), best, average and worst case](plots/part_a_cases.png)

## Part (b): adjacency lists + min-heap

Theory: `O((V + E) log V)`. `Build-Min-Heap` is `O(V)`, the `V`
extractions are `O(log V)` each, every list entry is read once, and each of
at most `E` decrease-keys climbs at most `log V` levels.

### (i) Varying V with E = 10V

`V` from 1,000 to 1,000,000; `E/V` fixed at 10; random weights.

Part (b) fixes `E/V` instead of `E` for two reasons. Over 10³ to 10⁶
vertices no single `E` fits `V ≤ E ≤ V(V − 1)`: `V = 1,000,000` needs at
least 1,000,000 edges, while `V = 1,000` allows at most 999,000. And with `E`
fixed, `(V + E) log V` is not a power of `V`: `E log V` dominates for small
`V` and `V log V` for large `V`, so the bound is not a straight line on a
log-log plot. With `E = 10V` it is `11 V log V`, nearly a straight line of
slope 1.10 over this range.

| V | E | Time | Key comparisons ÷ `(V+E) log₂V` | Decrease-keys ÷ V | Time per key comparison |
|---:|---:|---:|---:|---:|---:|
| 1,000 | 10,000 | 0.106 ms | 0.278 | 1.990 | 3.5 ns |
| 10,000 | 100,000 | 1.326 ms | 0.255 | 1.995 | 3.6 ns |
| 100,000 | 1,000,000 | 24.74 ms | 0.240 | 1.995 | 5.6 ns |
| 1,000,000 | 10,000,000 | 487.9 ms | 0.230 | 1.995 | 9.7 ns |

- **The counts follow the theory.** Key comparisons (queue + relaxations)
  divided by `(V + E) log₂V` stay between 0.23 and 0.28 over three orders of
  magnitude. On a log-log plot the count grows with slope 1.07, against 1.10
  for `(V + E) log V`: a little lower, because the `E` part costs no
  `log V` when few relaxations lower a key.
- **Decrease-keys.** 1.99-2.00 per vertex, below the bound
  `H(10) = 2.93` per vertex derived in the theory document.
- **The time grows faster than the counts** (slope 1.23), because each
  operation gets slower as the graph outgrows the CPU caches. The cost per
  key comparison is 3.4-3.6 ns up to `V = 10,000` and rises to 9.7 ns at
  `V = 1,000,000`. This is a property of the machine, not of the algorithm.
- The matrix of part (a) could not run here: `V = 1,000,000` would need 4 TB.

![Part (b)(i), varying V](plots/part_b_vary_v.png)

### (ii) Varying E at V = 5,000

`E` from 5,000 to 24,995,000; `V` fixed at 5,000; random weights; the same
graphs as (a)(ii).

| E | Density | Time | Heap comparisons | Decrease-keys | `V · H(E/V)` |
|---:|---:|---:|---:|---:|---:|
| 5,000 | 0.0002 | 0.224 ms | 59,557 | 4,999 | 5,000 |
| 1,000,000 | 0.04 | 1.735 ms | 149,683 | 23,921 | 29,390 |
| 12,500,000 | 0.5 | 12.07 ms | 143,045 | 32,910 | 42,007 |
| 17,500,000 | 0.7 | 16.46 ms | 135,749 | 33,492 | 43,689 |
| 24,995,000 | 1.0 | 21.40 ms | 127,519 | 34,022 | 45,472 |

- **The time grows almost linearly in `E`,** from 0.224 to 21.40 ms. A
  least-squares line `a + b · E` has `b = 0.86` ns per edge
  (`a = 0.77` ms) and stays within 0.91 ms of every point.
- **The heap work hardly grows.** From `E = 10,000` on, the heap comparisons
  stay between 114,313 and 154,978 while `E` grows 2,500-fold. The
  decrease-keys stay below `V · H(E/V)`: on the complete graph only 34,022
  of the 25 million relaxations lower a key (0.14%), against the bound
  45,472. So the time is the `Θ(E)` cost of reading the lists plus a heap
  term that barely depends on `E`, as the theory for random weights
  predicts: `Θ(E) + O(V log V · (1 + log(E/V)))`.

![Part (b)(ii), varying E](plots/part_b_vary_e.png)

### (iii) Best, average and worst case at V = 5,000

The sweep of (ii) with uniform, random and worst-case weights, on the same
graphs as (a)(iii).

| E | Density | Time, uniform | Time, random | Time, worst case | Decrease-keys, uniform | Decrease-keys, random | Decrease-keys, worst case |
|---:|---:|---:|---:|---:|---:|---:|---:|
| 5,000 | 0.0002 | 0.218 ms | 0.224 ms | 0.227 ms | 4,999 | 4,999 | 4,999 |
| 1,000,000 | 0.04 | 1.042 ms | 1.735 ms | 16.79 ms | 4,999 | 23,921 | 502,410 |
| 12,500,000 | 0.5 | 11.39 ms | 12.07 ms | 194.6 ms | 4,999 | 32,910 | 6,251,326 |
| 17,500,000 | 0.7 | 15.42 ms | 16.46 ms | 260.9 ms | 4,999 | 33,492 | 8,750,640 |
| 24,995,000 | 1.0 | 20.16 ms | 21.40 ms | 340.1 ms | 4,999 | 34,022 | 12,497,500 |

- **At fixed `V` all three grow about linearly in `E`, at very different
  rates.** `(V + E) log V` is linear in `E` when `V` is fixed.
  Least-squares lines over the 17 edge counts give 0.84 ns per edge
  (uniform), 0.86 ns (random) and 14.3 ns (worst case). On the complete graph the worst case takes 15.9 times as long as
  random weights.
- **Best case.** Exactly 4,999 decrease-keys at every `E`, and the fastest
  of the three at every `E`. Below `E = 1,000,000` it needs as little as
  0.42 of the random-weight time (0.300 against 0.720 ms at
  `E = 100,000`). Besides lowering fewer keys, equal weights give many
  equal keys, and `Min-Heapify` stops at the first child whose key is not
  smaller: at `E = 1,000,000` the uniform runs make 26,494 heap comparisons,
  random weights 149,683. On dense graphs reading the lists dominates both:
  20.16 against 21.40 ms on the complete graph.
- **The one bump in the best case,** at `E = 10,000` (0.399 ms, against
  0.218 ms at `E = 5,000` and 0.271 ms at `E = 50,000`), has the most heap
  comparisons (105,303). With two edges per vertex the search spreads over
  many levels, and most extractions move a vertex with key `∞` from the
  bottom of the heap to the root, from where it sinks past every finite
  key. With one edge per vertex there is only one finite key at a time;
  with ten or more, most vertices soon share the same few keys.
- **Worst case: each decrease-key climbs most of the heap.** On the complete
  graph the worst case makes 132,808,399 heap comparisons, 132,680,880 more
  than random weights for 12,463,478 more decrease-keys: 10.6 comparisons
  per decrease-key. A climb of `L` levels makes `L` comparisons if it
  reaches the root and `L + 1` if it stops below it. It stops one level
  below the root when the next cycle vertex already holds the root, which
  happens for the decrease-keys that come after the cycle edge in the
  vertex's list: about half, because lists are sorted by vertex number, not
  by position on the cycle. So each decrease-key climbs about 10.1 levels,
  out of at most 12 (`log₂ 5,000 = 12.3`).
- Measured time divided by the fitted `c · (V + E) log₂V` curve falls from
  1.12 at `E = 100,000` to 0.74 on the complete graph: the time grows like
  the `O((V + E) log V)` bound, a little more slowly than it over this
  range.

![Part (b)(iii), best, average and worst case](plots/part_b_cases.png)

## Part (c): which implementation is better, and when

All four combinations ran on the same graphs, over `V` = 1,000 to 8,000 and
16 densities from 0.001 to 1, in a shuffled order in each trial. The table
gives the median over trials of time(b) ÷ time(a) on the same graph: below 1
means (b) is faster.

| Weights | V | Density 0.001 | 0.1 | 0.5 | 1.0 |
|---|---:|---:|---:|---:|---:|
| random | 1,000 | 0.040 | 0.231 | 0.582 | 1.005 |
| random | 2,000 | 0.046 | 0.180 | 0.538 | 0.973 |
| random | 4,000 | 0.028 | 0.139 | 0.551 | 0.959 |
| random | 8,000 | 0.018 | 0.135 | 0.489 | 0.925 |
| worst case | 1,000 | 0.038 | 1.050 | 2.224 | 9.235 |
| worst case | 2,000 | 0.055 | 1.111 | 2.413 | 10.358 |
| worst case | 4,000 | 0.043 | 1.196 | 2.635 | 11.633 |
| worst case | 8,000 | 0.034 | 1.306 | 2.820 | 12.564 |

With worst-case weights, (b) stops being faster between the grid densities
0.05 and 0.1 at every `V`. The last two columns interpolate inside that
bracket:

| V | (b)/(a) at 0.05 | (b)/(a) at 0.1 | Crossover density | Crossover × `log₂V` |
|---:|---:|---:|---:|---:|
| 1,000 | 0.664 | 1.050 | 0.091 | 0.91 |
| 2,000 | 0.685 | 1.111 | 0.084 | 0.92 |
| 4,000 | 0.722 | 1.196 | 0.075 | 0.90 |
| 8,000 | 0.784 | 1.306 | 0.067 | 0.86 |

With random weights, (b) is faster at every density below 1 for every `V`.
On the complete graph the two tie at `V = 1,000` (1.005), and (b) is 3-8%
faster for the larger `V`.

![Part (c)](plots/part_c_comparison.png)

### Conclusions

- **Sparse graphs: (b), by a wide margin.** At density 0.001, (b) is 18 to
  57 times faster; at `V = 8,000` it is 57 times faster with random weights
  and 30 times with worst-case weights. On the identical `V = 5,000` graphs
  of (a)(ii) and (b)(ii), (a) takes 99.5 times as long as (b) on a single
  cycle (`E = V`) and 36.2 times as long at `E = 10V`. (a) reads `V²` cells
  and scans `V(V − 1)/2` queue entries whatever the graph looks like; (b)
  only touches what is there.
- **Dense graphs where few relaxations lower a key (random weights): (b),
  by a margin that shrinks as the graph fills up.** (b) reads the `E` list
  entries, (a) all `V²` matrix cells. At density 0.5, (b) takes 0.49-0.58 of
  the time of (a); on the complete graph the two are level (0.925-1.005). The
  same holds on the `V = 5,000` sweeps of parts (a) and (b): (a) takes 1.92
  times as long as (b) at density 0.5 and 1.07 times on the complete graph.
  The `log V` factor of (b) never shows, because at density 0.5 and above at
  most 1.1% of relaxations call `decreaseKey` (1.1% at `V = 1,000`, 0.17% at
  `V = 8,000`).
- **Dense graphs where most relaxations lower a key (worst case): (a), by up
  to 12.6 times at V = 8,000 on a complete graph.** Here the difference
  between `O(1)` and `O(log V)` per `decreaseKey` decides the result. The
  crossover lies between densities 0.05 and 0.1 (interpolated 0.067-0.091)
  and moves lower as `V` grows: at both grid points the ratio rises with
  `V`. While `V` grows 8-fold, the interpolated crossover falls by 27%,
  close to the 23% fall of the theoretical break-even density `1 / log V`
  (from `E ≈ V² / log V`). Parts (a)(iii) and (b)(iii) agree at
  `V = 5,000`: with worst-case weights (b) takes 0.62 of the time of (a) at
  density 0.04, 1.23 times as long at 0.1, and 12.2 times as long on the
  complete graph.
- **Which component matters** (`V = 8,000`, median times):

  | | Matrix + array (a) | Matrix + heap | Lists + array | Lists + heap (b) |
  |---|---:|---:|---:|---:|
  | Random, density 0.001 | 59.0 ms | 60.2 ms | 12.2 ms | 1.0 ms |
  | Worst case, density 1.0 | 77.5 ms | 971.2 ms | 86.2 ms | 962.1 ms |

  On sparse graphs, both parts of (a) cost something: switching only to
  lists still leaves 12.2 ms of array scanning, and switching only to a heap
  leaves the 60 ms matrix scan. Only (b), which changes both, gets down to
  1 ms. On the worst-case complete graph the queue alone decides: with or
  without the matrix, the heap versions take about 960-970 ms and the array
  versions 77-86 ms.
- **Memory.** The matrix needs `4V²` bytes whatever `E` is; the lists need
  about `8E + 40V` bytes.

  | Graph | Matrix | Lists |
  |---|---:|---:|
  | `V = 8,000`, density 0.001 | 256 MB | 0.8 MB |
  | `V = 8,000`, density 0.5 | 256 MB | 256 MB |
  | `V = 8,000`, complete | 256 MB | 512 MB |
  | `V = 1,000,000`, `E = 10⁷` | 4 TB | 120 MB |

  The lists use less memory below density 0.5 and twice as much as the
  matrix on a complete graph, because each edge stores its target as well as
  its weight, while a matrix cell stores only the weight. Above a few tens of
  thousands of vertices the matrix does not fit in memory at all.
- **Reachability.** If the source reaches only a small part of the graph,
  (a) still reads `V` cells for every vertex it extracts, while (b) only
  touches the reachable part after its `O(V)` `Build-Min-Heap`.

In short: use (b) unless the graph is dense and most relaxations are expected
to lower a distance, or unless a matrix is needed anyway for `O(1)` edge
lookups and memory allows it.

## Run

Run these commands from the `project-2` folder. The plots need `matplotlib`.

```bash
javac -d out java/*.java
```

```bash
java -cp out CorrectnessCheck
```

```bash
java -Xmx6g -cp out ExperimentRunner results
```

```bash
python3 python/plot_results.py results
```

`ExperimentRunner` takes an optional second argument naming the parts to
run, so one part can be rerun without overwriting results the report
already quotes. The committed results of parts (a) and (b) come from
`java -Xmx6g -cp out ExperimentRunner results a` and `... results b`, which
take about 1.5 minutes each; part (c), the longest, comes from an earlier
`... results c`. CSV data is written to `results/` and figures to `plots/`.

## Appendix: why the matrix scan has no if-statement

This is backup material for part (a): it explains a choice in the code,
not the algorithm, and it is the same effect that makes the worst case of
(a)(iii) slower at middle densities.

The textbook loop reads "for each `v` adjacent to `u`", that is
`if (row[v] != NO_EDGE) { relax }`. The CPU guesses the outcome of every
`if` before it is known, and throws work away when the guess is wrong. In a
random row of density 0.5, "is there an edge here?" is a coin flip.

`DijkstraMatrixArray` therefore treats a missing edge as weight `∞` (the CLRS
convention for weight matrices) and computes `d[u] + w` or `∞` with a bit
mask (`AdjacencyMatrixGraph.distanceThrough`). The only `if` left is the
relaxation test, which almost always fails on random weights. The control
(`MatrixScanControl`) runs both versions on the same graphs at `V = 4,000`:

| Density | 0.001 | 0.1 | 0.3 | 0.5 | 0.7 | 0.9 | 1.0 |
|---|---:|---:|---:|---:|---:|---:|---:|
| No `if` (shipped) | 14.3 ms | 14.5 ms | 14.8 ms | 15.0 ms | 14.9 ms | 14.6 ms | 14.6 ms |
| Separate `if` (control) | 10.1 ms | 18.8 ms | 39.1 ms | 57.1 ms | 39.4 ms | 21.0 ms | 11.9 ms |

- Both versions make the same relaxations and decrease-keys. The control
  skips an empty cell after one test, while the shipped code computes a mask
  and compares in every cell. So where the edge test is easy to guess, the
  control is faster: 10.1 against 14.3 ms at density 0.001.
- At density 0.5 the control's wrong guesses make it 3.8 times slower. Its
  curve is symmetric about 0.5 (39.1 ms at 0.3, 39.4 ms at 0.7): an edge is
  present with probability `d` in every row, so the share of wrong guesses
  is `min(d, 1 − d)`. Without the change, measured time would rise and fall
  with `E` for a reason that has nothing to do with the algorithm. That
  would also distort part (c), where most comparisons happen at middle
  densities.
- The price is that on the sparsest graphs the shipped (a) is about 1.4 times
  slower than the textbook loop, so part (c)'s sparse-graph margins of (b)
  are about 1.4 times larger than they would be against the textbook loop.

![Part (a) control](plots/part_a_branch_control.png)
