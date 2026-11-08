# Theoretical Analysis

Notation: `C` is the capacity and `n` the number of object types. Type `i` has
weight `wᵢ` and profit `pᵢ`, all positive integers, and its supply is
unlimited. `P(C)` is the largest total profit of any multiset of objects whose
total weight is at most `C`.

## The recursive definition is correct

```text
P(C) = 0                                  if wᵢ > C for every i
P(C) = max { pᵢ + P(C − wᵢ) : wᵢ ≤ C }    otherwise
```

If no object fits, the empty set is the only packing, so `P(C) = 0`. Otherwise
write `R` for the right-hand side and prove `P(C) = R` in two steps.

- **`P(C) ≤ R`.** Some object fits and all profits are positive, so an optimal
  packing `S` is not empty. Take any object of `S`, of type `i`; then
  `wᵢ ≤ C`. The rest of `S` fits in `C − wᵢ`, so it earns at most
  `P(C − wᵢ)`, and `P(C) ≤ pᵢ + P(C − wᵢ) ≤ R`.
- **`P(C) ≥ R`.** For any `i` with `wᵢ ≤ C`, take an optimal packing of
  `C − wᵢ` and add one object of type `i`. The supply is unlimited, so this is
  allowed. The result fits in `C` and earns `pᵢ + P(C − wᵢ)`, so
  `P(C) ≥ pᵢ + P(C − wᵢ)` for every such `i`.

The knapsack need not be full, and the recurrence needs no extra `P(C − 1)`
term for that: the first step holds for any optimal packing, however much
capacity it leaves unused.

## Why one parameter is enough

The 0/1 knapsack of lecture 10 needs a second parameter, `P(C, j)`, to record
which objects are still available. With unlimited supplies, all `n` types stay
available after any choice, so the capacity alone identifies a subproblem.

Two-parameter recurrences are also correct here, but the assignment defines
`P(C)` with one parameter, and the first of these two is slower:

```text
P(c, j) = max { P(c − k·wⱼ, j − 1) + k·pⱼ : k ≥ 0, k·wⱼ ≤ c }    (how many of type j)
P(c, j) = max( P(c, j − 1), pⱼ + P(c − wⱼ, j) )                 (one more of type j)
```

with `P(c, 0) = 0`, and with the second term of the second form only when
`wⱼ ≤ c`. In the first form `k` must range over all feasible counts; limiting
it to 0 and 1 gives the 0/1 knapsack, and `P(16)` for the weights of part (2)
would be 16 instead of 28. Each state tries `⌊c/wⱼ⌋ + 1` counts, so the total
work is `Θ(nC + C² · Σⱼ 1/wⱼ)`, which is `Θ(nC²)` when the weights are
constants. The second form is the 0/1 recurrence with `j − 1` changed to `j` in
the term that takes an object, and it costs `Θ(nC)`, the same as `P(C)`.

## The subproblem graph

As defined in lecture 10, the vertices are the instances, and `(I, J)` is an
edge when computing `I` makes a direct recursive call on `J`. So `P(c)` has one
edge to `P(c − wᵢ)` for every `wᵢ ≤ c`.

- Every weight is at least 1, so every edge leads to a smaller capacity. The
  graph is therefore acyclic, and increasing capacity is a valid order in which
  to compute the vertices.
- `P(c)` is a vertex of the graph of `P(C)` exactly when `C − c` is a sum of
  weights, each used any number of times. For `C = 15` and `w = (4, 5, 8)`, the
  values of `C − c` that work are 0, 4, 5, 8, 9, 10, 12, 13, 14 and 15. This
  gives the 10 vertices 15, 11, 10, 7, 6, 5, 3, 2, 1 and 0.
- The vertices 15, 11 and 10 have 3 edges each, and 7, 6 and 5 have 2 each. The
  vertices 3, 2, 1 and 0 have none, since the smallest weight is 4. That makes
  15 edges.

## Complexity of the bottom-up algorithm

```text
MaxProfit(w[0..n-1], p[0..n-1], C):
    P[0] = 0
    for c = 1 to C:
        P[c] = 0
        for i = 0 to n - 1:
            if w[i] ≤ c and p[i] + P[c - w[i]] > P[c]:
                P[c] = p[i] + P[c - w[i]]
    return P[C]
```

The outer loop runs `C` times and the inner loop `n` times with `O(1)` work, so
the time is `Θ(nC)`. The array has `C + 1` entries, so the space is `Θ(C)`.
The algorithm computes all capacities from 0 to `C`, including those that are
not vertices of the graph of `P(C)`. A top-down version with memoisation would
visit only the vertices, but its worst case is still `Θ(nC)`: when some weight
is 1, every capacity is a vertex.

As lecture 10 says of the 0/1 knapsack, this is pseudo-polynomial. The input
writes `C` as one number of `Θ(log C)` bits, so `Θ(nC)` is polynomial in the
value of `C` and exponential in the size of its representation.
