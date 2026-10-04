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
| `java/MatrixScanControl.java` | Part (a) control: the matrix scan written with a plain `if` on the edge test |
| `java/ShortestPaths.java` | Result of one run: distances and predecessors |
| `java/GraphGenerator.java` | Random graphs with exact `V` and `E`, in two weightings of the same edges |
| `java/ShortestPathVerifier.java` | Checks any result against the graph in `O(V + E)` |
| `java/CorrectnessCheck.java` | CLRS worked example, 525 random graphs against Bellman-Ford, generator checks |
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
- **Two weightings of the same edges.**
  - *Random weights:* uniform integers from 1 to 1,000.
  - *Worst-case weights:* chosen so that every relaxation of a forward edge
    lowers a key below every other key in the queue, except possibly the key
    of the next vertex on the cycle. In the heap, every such `decreaseKey`
    climbs to the root or to a child of the root. This is the input behind
    the `E log V` term of (b), which random weights never reach.
    `CorrectnessCheck` confirms that the decrease-key count equals the number
    of forward edges.
- **Seeds.** The base seed is `20261004`. A given `(V, E, trial)` always
  produces the same edges, in every part and in both weightings.
- **Trials.** Each trial of a configuration uses a new graph, so no
  implementation is timed twice in a row on the same graph. Repeating one
  graph lets the CPU learn the run and makes short runs look faster. Each
  configuration gets at least 10 trials; small graphs get more, until 100 ms
  of CPU time has been measured (at most 100 trials).
- **Timing.** Current-thread CPU time of the `run` call alone. Graph
  generation, building the matrix, and verification are not timed. Medians
  are reported. The JVM is warmed up on every implementation first. All runs
  were on an Apple M2 Pro with Java 23 and `-Xmx6g`.
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
decrease-keys cost `O(1)` each, and `E ≤ V(V − 1)`.

### Varying V

`E = 10V` (sparse) and `E = V(V − 1)` (complete), random weights.

| V | Time, `E = 10V` | Time, complete | Cells read | Queue comparisons |
|---:|---:|---:|---:|---:|
| 500 | 0.252 ms | 0.254 ms | 250,000 | 124,750 |
| 1,000 | 0.964 ms | 0.936 ms | 1,000,000 | 499,500 |
| 2,000 | 3.811 ms | 3.816 ms | 4,000,000 | 1,999,000 |
| 4,000 | 15.47 ms | 14.64 ms | 16,000,000 | 7,998,000 |
| 8,000 | 58.75 ms | 59.86 ms | 64,000,000 | 31,996,000 |

- The time grows with slope 1.97 on a log-log plot, for both families,
  against 2 for `V²`. Time divided by the `V²` cells read is 0.91-1.01 ns,
  which includes the `V(V − 1)/2` queue comparisons.
- The sparse and the complete graph take the same time to within 6% at every
  `V`, even though the complete graph has 800 times more edges at
  `V = 8,000`.
- In every row, cells read is exactly `V²`, queue comparisons exactly
  `V(V − 1)/2`, and relaxations exactly `E`.

![Part (a), varying V](plots/part_a_vary_v.png)

### Varying E at V = 5,000

| E | Density | Time, random weights | Time, worst-case weights | Decrease-keys, worst case |
|---:|---:|---:|---:|---:|
| 5,000 | 0.0002 | 23.62 ms | 22.72 ms | 4,999 |
| 1,000,000 | 0.04 | 22.60 ms | 27.70 ms | 502,410 |
| 12,500,000 | 0.5 | 23.21 ms | 75.16 ms | 6,251,326 |
| 17,500,000 | 0.7 | 23.27 ms | 82.26 ms | 8,750,640 |
| 24,995,000 | 1.0 | 22.78 ms | 28.66 ms | 12,497,500 |

- **Random weights.** The time stays between 22.5 and 23.6 ms while `E`
  grows 5,000-fold, as `Θ(V²)` predicts.
- **Worst-case weights.** Half the edges now lower a key. Each
  `decreaseKey` is still `O(1)`, but the CPU cannot predict which cells will
  succeed when they are scattered at random. At density 0.7 this costs
  59.0 ms more than random weights on the same edges, about 6.7 ns per
  decrease-key. On the complete graph the cells that succeed are exactly the
  unfinished vertices, nearly the same set row after row, so the CPU guesses
  right again and the time falls back to 28.7 ms. This work is bounded by the
  `V²/2` relaxations, so the cost is still `Θ(V²)`, with a larger constant.

![Part (a), varying E](plots/part_a_vary_e.png)

### Why the matrix scan has no if-statement

The textbook loop reads "for each `v` adjacent to `u`", that is
`if (row[v] != NO_EDGE) { relax }`. The CPU guesses the outcome of every
`if` before it is known, and throws work away when the guess is wrong. In a
random row of density 0.5, "is there an edge here?" is a coin flip.

`DijkstraMatrixArray` therefore treats a missing edge as weight `∞` (the CLRS
convention for weight matrices) and computes `d[u] + w` or `∞` with a bit
mask (`AdjacencyMatrixGraph.distanceThrough`). The only `if` left is the
relaxation test, which almost always fails on random weights. The control
runs both versions on the same graphs at `V = 4,000`:

| Density | 0.001 | 0.1 | 0.3 | 0.5 | 0.7 | 0.9 | 1.0 |
|---|---:|---:|---:|---:|---:|---:|---:|
| No `if` (shipped) | 15.4 ms | 15.6 ms | 15.2 ms | 15.7 ms | 15.1 ms | 15.3 ms | 14.7 ms |
| Separate `if` (control) | 11.0 ms | 20.3 ms | 39.6 ms | 59.5 ms | 41.5 ms | 23.0 ms | 13.7 ms |

- Both versions make the same relaxations and decrease-keys. The control
  skips an empty cell after one test, while the shipped code computes a mask
  and compares in every cell. So where the edge test is easy to guess, the
  control is faster: 11.0 against 15.4 ms at density 0.001.
- At density 0.5 the control's wrong guesses make it 3.8 times slower.
  Without the change, measured time would rise and fall with `E` for a reason
  that has nothing to do with the algorithm. That would also distort part
  (c), where most comparisons happen at middle densities.
- The price is that on the sparsest graphs the shipped (a) is about 1.4 times
  slower than the textbook loop, so part (c)'s sparse-graph margins of (b)
  are about 1.4 times larger than they would be against the textbook loop.

![Part (a) control](plots/part_a_branch_control.png)

## Part (b): adjacency lists + min-heap

Theory: `O((V + E) log V)`. `Build-Min-Heap` is `O(V)`, the `V`
extractions are `O(log V)` each, every list entry is read once, and each of
at most `E` decrease-keys climbs at most `log V` levels.

### Varying V with E = 10V

| V | Time, random | Time, worst case | Key comparisons ÷ `(V+E) log₂V`, random | Same, worst case |
|---:|---:|---:|---:|---:|
| 1,000 | 0.108 ms | 0.216 ms | 0.278 | 0.599 |
| 10,000 | 1.363 ms | 2.897 ms | 0.255 | 0.618 |
| 100,000 | 22.43 ms | 39.56 ms | 0.240 | 0.631 |
| 1,000,000 | 423.7 ms | 785.7 ms | 0.230 | 0.639 |

- **The counts follow the theory.** Key comparisons (queue + relaxations)
  divided by `(V + E) log₂V` stay between 0.23 and 0.28 (random) and between
  0.60 and 0.64 (worst case) over three orders of magnitude. On a log-log
  plot the count grows with slope 1.11 for worst-case weights, against 1.10
  for `(V + E) log V`. For random weights it is 1.07: lower, because the `E`
  part costs no `log V` when few relaxations lower a key.
- **Decrease-keys.** Random weights give 1.99 per vertex, below the bound
  `H(10) = 2.93` per vertex derived in the theory document. Worst-case
  weights give 5.5 per vertex: one per forward edge.
- **The time grows faster than the counts** (slope 1.19 random, 1.17 worst
  case), because each operation gets slower as the graph outgrows the CPU
  cache. The cost per key comparison rises from 3.6 to 8.4 ns with random
  weights and from 3.3 to 5.6 ns with worst-case weights between
  `V = 1,000` and `V = 1,000,000`. This is a property of the machine, not of
  the algorithm.
- The matrix of part (a) could not run here: `V = 1,000,000` would need 4 TB.

![Part (b), varying V](plots/part_b_vary_v.png)

### Varying E at V = 5,000

| E | Time, random | Time, worst case | Decrease-keys, random | Decrease-keys, worst case |
|---:|---:|---:|---:|---:|
| 5,000 | 0.230 ms | 0.243 ms | 4,999 | 4,999 |
| 1,000,000 | 1.785 ms | 17.33 ms | 23,915 | 502,410 |
| 24,995,000 | 23.75 ms | 348.0 ms | 34,022 | 12,497,500 |

- **Random weights.** The time grows linearly in `E`, at 0.92 ns per extra
  edge. Only 34,022 of the 25 million relaxations on the complete graph lower
  a key (0.14%), below the `V · H(E/V) = 45,472` bound. So the heap does
  little work, and the measured cost is close to `Θ(E + V log V)`.
- **Worst-case weights.** On the complete graph every one of the `E/2`
  forward edges lowers a key. Each `decreaseKey` makes 10.6 key comparisons
  and climbs 10.1 heap levels on average, out of at most 12
  (`log₂ 5,000 = 12.3`). Between `E = 1,000,000` and the complete graph the
  time grows at 13.8 ns per extra edge. Measured time divided by the fitted
  `c · (V + E) log₂V` curve falls from 1.14 at `E = 100,000` to 0.74 on the
  complete graph: the time grows like the `O((V + E) log V)` bound, a little
  more slowly than it over this range.

![Part (b), varying E](plots/part_b_vary_e.png)

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
  and 30 times with worst-case weights. With `E = 10V` (parts (a) and (b)),
  (a) takes 8.9 times as long as (b) at `V = 1,000` and 36.8 times as long at
  `V = 5,000`: a 4.1-fold rise, the same as the `V / log V` growth the theory
  predicts. (a) reads `V²` cells and scans `V(V − 1)/2` queue entries
  whatever the graph looks like; (b) only touches what is there.
- **Dense graphs where few relaxations lower a key (random weights): (b),
  by a margin that shrinks as the graph fills up.** (b) reads the `E` list
  entries, (a) all `V²` matrix cells. At density 0.5, (b) takes 0.49-0.58 of
  the time of (a); on the complete graph the two are level (0.925-1.005). The
  `log V` factor of (b) never shows, because at density 0.5 and above at
  most 1.1% of relaxations call `decreaseKey` (1.1% at `V = 1,000`, 0.17% at
  `V = 8,000`).
- **Dense graphs where most relaxations lower a key (worst case): (a), by up
  to 12.6 times at V = 8,000 on a complete graph.** Here the difference
  between `O(1)` and `O(log V)` per `decreaseKey` decides the result. The
  crossover lies between densities 0.05 and 0.1 (interpolated 0.067-0.091)
  and moves lower as `V` grows: at both grid points the ratio rises with
  `V`. While `V` grows 8-fold, the interpolated crossover falls by 26%,
  close to the 23% fall of the theoretical break-even density `1 / log V`
  (from `E ≈ V² / log V`).
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

The full experiment takes about 10 minutes. `ExperimentRunner` takes an
optional second argument naming the parts to run, for example
`java -Xmx6g -cp out ExperimentRunner results c`, so one part can be rerun
without overwriting results the report already quotes. CSV data is written to
`results/` and figures to `plots/`.
