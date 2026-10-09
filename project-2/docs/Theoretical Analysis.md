# Theoretical Analysis

Notation: `V = |V|` is the number of vertices and `E = |E|` the number of
edges. Graphs are directed with positive integer weights. `log` means `log₂`.
`deg⁺(u)` is the number of edges leaving `u`. The *density* of a graph is
`E / (V(V − 1))`, the fraction of all possible edges that are present.

## The common skeleton

All four implementations run the algorithm of CLRS (4th ed., Section 22.3)
with one added line: stop when the extracted vertex is unreachable. Only the
graph representation and the priority queue change.

```text
for each vertex v: d[v] = ∞, π[v] = nil
d[s] = 0
Q = all vertices, keyed by d            // build the priority queue
while Q is not empty:
    u = Extract-Min(Q)                  // d[u] is now final
    if d[u] = ∞: stop                   // every vertex left is unreachable
    for each edge (u, v):               // how edges are found depends on the representation
        if d[u] + w(u, v) < d[v]:       // one "relaxation"
            d[v] = d[u] + w(u, v); π[v] = u
            Decrease-Key(Q, v, d[v])
```

As in CLRS, every edge of `u` is relaxed, including edges into vertices that
are already finished. Such a relaxation never succeeds: a finished `v` was
extracted earlier, so `d[v] ≤ d[u] < d[u] + w(u, v)`.

The running time splits into five parts:

```text
T = T(build Q) + (#Extract-Min)·T(Extract-Min) + T(find all edges)
    + (#relaxations)·O(1) + (#Decrease-Key)·T(Decrease-Key)
```

Let `R` be the number of vertices reachable from `s`. In all our experiment
graphs every vertex is reachable, so `R = V`.

- `Extract-Min` runs `R` times (once more if some vertex is unreachable).
- Every edge out of a reachable vertex is relaxed once, so there are at most
  `E` relaxations, and exactly `E` when `R = V`.
- A relaxation calls `Decrease-Key` only when it finds a shorter path, so
  there are at most `E` calls.

## Best, average and worst case

The numbers of extractions, edge scans and relaxations above do not depend
on the weights. Only the number of `Decrease-Key` calls does, and it lies
between `R − 1` and `E`. The experiments use three weightings of the same
edges, one for each case.

### Best case: uniform weights, exactly `V − 1` calls

Every reachable vertex other than `s` starts with `d = ∞` and ends with a
finite distance, and the first relaxation that reaches it lowers its key
from `∞`. So any weighting makes at least `R − 1` `Decrease-Key` calls.

Uniform weights (every weight 1, `GraphGenerator.uniformGraph`) make
exactly that many. With equal weights, `d(v) = d(u) + 1` for an
in-neighbour `u` of `v` with the smallest distance. Vertices are extracted in
non-decreasing order of distance, so the first in-neighbour of `v` to be
extracted is such a `u`, and its relaxation already sets `d[v]` to its final
value. Every later relaxation into `v` comes from an in-neighbour `u'` with
`d(u') ≥ d(u)` and offers `d(u') + 1 ≥ d[v]`, so it fails. Each vertex other
than `s` therefore has its key lowered exactly once: `R − 1` calls, which is
`V − 1` when every vertex is reachable. `CorrectnessCheck` confirms the count
for all four implementations.

### Average case: random weights, at most about `V · H(E/V)` calls

`d[v]` is lowered only when a newly examined in-edge of `v` gives a shorter
path than every in-edge examined before it. The in-neighbours of `v` are
extracted in order of distance, so a later in-neighbour `u` can only win if
its edge weight `w(u, v)` is smaller than all earlier ones. This order does
not depend on the weights of `v`'s own in-edges: an in-neighbour `u`
extracted before `v` has `d[u] ≤ d[v]`, so its shortest path does not pass
through `v`, and an in-neighbour extracted after `v` cannot lower `d[v]` at
all. So the `j`-th in-edge weight in this order is smaller than all earlier
ones with probability at most `1/j`. Among `k` in-edges, the expected number
of "new smallest so far" events is therefore at most the harmonic number

```text
H(k) = 1 + 1/2 + 1/3 + ... + 1/k ≈ ln k + 0.58.
```

Each vertex `v` contributes at most `H(in-degree of v)`. The average
in-degree is `E/V`, and `H` grows more slowly as its argument grows, so the
expected number of `Decrease-Key` calls is at most
`V · H(E/V) ≈ V(ln(E/V) + 0.58)`, far below `E` when `E/V` is large (Noshita,
1985, proves an `O(V(1 + log(E/V)))` bound for random weights).

### Worst case: worst-case weights, about `E/2` calls

`GraphGenerator.worstCaseGraph` keeps the same edges and picks weights that
make as many relaxations as possible lower a key. Every generated graph
contains a cycle through all vertices (a Hamiltonian cycle). Number the
vertices `x₀ = s, x₁, x₂, ...` along it and give each cycle edge weight 1, so
`d(xᵢ) = i` and the vertices are extracted in exactly this order. Every other
edge from `xᵢ` to a later vertex (a *forward edge*) gets a weight chosen so
that its new key `d(xᵢ) + w` is smaller than every key set earlier, and these
keys fall along `xᵢ`'s adjacency list. So every relaxation of a forward edge
lowers a key below every other key in the queue, except `xᵢ₊₁`'s key `i + 1`
once the cycle edge to `xᵢ₊₁` has been relaxed. Edges back to earlier
vertices never lower a key, because their target is already finished. With
about half the edges pointing forward, `Decrease-Key` runs about `E/2` times.

## Part (a): adjacency matrix + array priority queue

The queue is an unsorted array of `(vertex, key)` entries, plus an index from
each vertex to its slot (`ArrayPriorityQueue`). A missing edge counts as
weight `∞`, the convention CLRS uses for weight matrices, so the test on its
cell always fails. These failed tests are part of the row-scan cost below; the
Relaxation row counts only the `E` tests on real edges.

| Step | Cost per call | Number of calls | Total |
|---|---|---|---|
| Build the queue | `Θ(V)` | 1 | `Θ(V)` |
| `Extract-Min`: scan every entry still in the queue | `Θ(|Q|)` | `V` | `Θ(V²)` |
| Find the edges of `u`: read the whole matrix row | `Θ(V)` | `V` | `Θ(V²)` |
| Relaxation | `O(1)` | `E` | `Θ(E)` |
| `Decrease-Key`: overwrite one key through the index | `O(1)` | `≤ E` | `O(E)` |

The extraction cost is exact. The k-th extraction scans the `V − k + 1`
entries left and makes `V − k` comparisons, so the total is

```text
(V − 1) + (V − 2) + ... + 1 + 0 = V(V − 1)/2 comparisons.
```

The row scans read exactly `V · V = V²` matrix cells. Neither number depends
on `E`.

Adding the rows of the table:

```text
T_a(V, E) = Θ(V) + Θ(V²) + Θ(V²) + Θ(E) + O(E) = Θ(V² + E) = Θ(V²),   because E ≤ V(V − 1).
```

- **With respect to `V`:** quadratic.
- **With respect to `E`:** `E` only enters through the relaxations and
  `Decrease-Key` calls, at `O(1)` each. That work is never larger than the
  `V²` cells already read.
- **Best, average and worst case:** `V − 1`, at most about `V · H(E/V)`, and
  about `E/2` `Decrease-Key` calls, each `O(1)`. All three cases are
  therefore `Θ(V²)` on graphs where every vertex is reachable. On real
  hardware the worst case still costs more per cell at middle densities,
  because the CPU often guesses the outcome of the relaxation test wrongly
  (README, part (a)(iii)). That changes the constant, not the `Θ(V²)`
  growth: each cell adds at most one wrong guess.
- **Reachability:** if only `R < V` vertices are reachable, the loop stops
  after `R + 1` extractions and `R` row scans, so the cost is `Θ(R · V)`.
  Each of those `R` row scans still reads all `V` cells, unreachable columns
  included.
- **Memory:** the matrix needs `V²` cells (`4V²` bytes as `int`), whatever `E` is.

## Part (b): array of adjacency lists + minimizing heap

The queue is a binary min-heap in the 0-based array layout of lecture 3, plus
an index from each vertex to its heap node so that `Decrease-Key` can find it
(`MinHeapPriorityQueue`).

| Step | Cost per call | Number of calls | Total |
|---|---|---|---|
| Build the queue: `Build-Min-Heap` | `O(V)` | 1 | `O(V)` |
| `Extract-Min`: move the last entry to the root, `Min-Heapify` | `O(log V)` | `V` | `O(V log V)` |
| Find the edges of `u`: read `u`'s list | `Θ(1 + deg⁺(u))` | `V` | `Θ(V + E)` |
| Relaxation | `O(1)` | `E` | `Θ(E)` |
| `Decrease-Key`: lower the key, move it up as in `Enqueue` | `O(log V)` | `≤ E` | `O(E log V)` |

The list scans add up to `Σ deg⁺(u) = E` (lecture 4). `Min-Heapify` makes at most
2 comparisons per level and the heap has `⌊log V⌋` levels below the root, so the
`V` extractions make at most `2V log V` comparisons. Each `Decrease-Key` climbs
at most `⌊log V⌋` levels.

Adding the rows of the table:

```text
T_b(V, E) = O(V) + O(V log V) + Θ(V + E) + Θ(E) + O(E log V) = O((V + E) log V).
```

When every vertex is reachable from the source, `E ≥ V − 1` and this is the
familiar `O(E log V)`.

- **With respect to `V`:** `V log V` from the extractions.
- **With respect to `E`:** `E` from reading the lists, plus up to `E log V`
  from the `Decrease-Key` calls.
- **Reachability:** after the `O(V)` `Build-Min-Heap`, only reachable
  vertices and their edges are touched.
- **Memory:** `Θ(V + E)`: the lists store each edge once, as a target and a weight.

### Running time in each case

The `E log V` term is reached only if most relaxations lower a key and each
lowered key climbs most of the heap. Whether that happens depends on the
weights (see [Best, average and worst case](#best-average-and-worst-case)
for the number of calls).

- **Best case, uniform weights.** `V − 1` `Decrease-Key` calls at
  `O(log V)` each, the same order as the extractions, so

  ```text
  T_b = Θ(V + E) + O(V log V).
  ```

- **Average case, random weights.** At most about `V · H(E/V)` calls on
  average. The heap work is then `O(V log V)` for the extractions plus
  `O(V log V · (1 + log(E/V)))` for the `Decrease-Key` calls, so the expected
  running time is

  ```text
  T_b = Θ(E) + O(V log V · (1 + log(E/V))),
  ```

  which, for a fixed `V`, grows linearly in `E`.

- **Worst case, worst-case weights.** About `E/2` calls. Each new key is
  smaller than every other key in the heap except possibly `xᵢ₊₁`'s, so the
  vertex climbs from its current node to the root, or to a child of the root
  when `xᵢ₊₁` holds the root: at most `⌊log V⌋` levels. On the complete graph
  with `V = 5,000` each call makes 10.6 heap comparisons on average (README,
  part (b)(iii)). A climb of `L` levels makes `L` comparisons if it reaches
  the root and `L + 1` if it stops below it, which happens for about half of
  the calls, so the average climb is about 10.1 levels, out of at most 12.
  This input therefore reaches the bound: `Θ(E log V)`.

## Part (c): which is better, and when

| | (a) matrix + array | (b) lists + heap |
|---|---|---|
| Time | `Θ(V²)` when every vertex is reachable (`Θ(R · V)` in general) | `O((V + E) log V)` worst case |
| Memory | `Θ(V²)` | `Θ(V + E)` |
| Finding the edges of `u` | `Θ(V)`, even if `u` has none | `Θ(1 + deg⁺(u))` |
| `Extract-Min` | `Θ(V)` | `O(log V)` |
| `Decrease-Key` | `O(1)` | `O(log V)` |

(a) pays `Θ(V)` per vertex no matter what. (b) pays per edge, plus `log V`
per heap operation.

- **Sparse graphs, `E = O(V)`.** (b) costs `O(V log V)` against `Θ(V²)` for
  (a). (b) wins by a factor of about `V / log V`, which grows without bound.
- **Dense graphs, `E = Θ(V²)`, worst case.** (b) costs `Θ(V² log V)` against
  `Θ(V²)` for (a). (a) wins by up to a `log V` factor, because its `O(1)`
  `Decrease-Key` handles the many successful relaxations more cheaply.
- **Break-even, worst case.** `(V + E) log V ≈ V²` gives `E ≈ V² / log V`,
  a density of about `1 / log V` (≈ 0.08 for `V = 8,000`), up to the constant
  factors of each implementation.
- **Break-even, random weights.** (b) does few `Decrease-Key` calls, so both
  costs are roughly proportional to the number of matrix cells or list
  entries read: `c_a·V²` against `c_b·E`. The break-even density is then a
  ratio of constants, `c_a / c_b`, and no longer shrinks as `V` grows. In
  the experiments (b) is faster at every density below 1 for every `V`,
  and the two meet only on the complete graph. So on the test machine
  `c_a / c_b` is about 1 or larger: with random weights, (a) is never clearly
  faster.

Three things that the time bounds do not show also decide the choice:

- **Memory.** The matrix needs `4V²` bytes even for a sparse graph: 400 MB for
  `V = 10,000` and 4 TB for `V = 1,000,000`. The lists need about `8E + 40V`
  bytes, so they use less memory below density 0.5 and about twice as much as
  the matrix on a complete graph: each edge stores its target (4 bytes) as
  well as its weight (4 bytes), while a matrix cell stores only the weight.
- **Reachability.** If the source reaches only `R` of the `V` vertices, (a)
  still costs `Θ(R · V)` while (b) costs `O(V + (R + E_R) log V)`, where `E_R`
  counts the edges among reachable vertices.
- **Edge lookup.** The matrix answers "is there an edge `(u, v)`, and how
  heavy is it?" in `O(1)`; the lists need `O(deg⁺(u))`. Dijkstra never asks
  that question, but other algorithms on the same graph might.

## Reference

K. Noshita, "A theorem on the expected complexity of Dijkstra's shortest path
algorithm," *Journal of Algorithms*, 6(3), 400–408, 1985.
