---
aliases:
tags:
related:
cssclasses:
excalidraw-open-md: "true"
created: 2026-09-16 21:07:38
updated: 2026-09-16 21:31:56
---
We will perform some time complexity analysis to determine how the cost function differ from that of merge sort which is $O(n\log n)$

Before our array is partitioned into size $S$, let $x$ be the number of recursive calls we need.
$$
n\left( \frac{1}{2} \right)^x=S
$$
Solving for $x$ gives $-\log\left( \frac{S}{n} \right)$.

## Insertion Sort
We first consider the cost of utilising Insertion Sort, it's time complexity is $O(n^2)$ for a $n$ sized array. For each array with size $S$, that is $O(S^2)$. In total we have $\frac{n}{S}$ such arrays, so the total cost from insertion sort is simply $\frac{n}{S}O(S^2)=O(nS)$.

## Merge Sort
From above, we will make a total of $-\log\left( \frac{S}{n} \right)$ recursive levels. Each layer does $n$ units of work, and in total the cost from merge sort is $O\left( -n\log\left( \frac{S}{n} \right) \right)$.

## Total Cost
Hence, theoretically total cost is
$$
O\left( nS-n\log\left( \frac{S}{n} \right) \right)=O(nS+n\log n-n\log S)
$$
>The RHS will be a more useful form for subsequent analysis

---
ci)
With $S$ fixed, and varying $n$, our time complexity becomes 
$$
O(nS+n\log n-n\log S) = O(n+n\log n-n) = O(n\log n)
$$
Making it theoretically similar to merge sort.

cii)
Fixing $n$ and varying $S$ gives us
$$
O(nS-n\log S) = O(S-\log S)
$$
When increasing $S$, we increase greater cost from insertion sort, and less from merge sort.

ciii)
To obtain our optimal $S$, we can differentiate the function with respect to $S$ to observe how it changes as $S$ changes.
$$
\frac{dO}{dS}= n -\frac{n}{S\ln_{2}}
$$
Setting it to be $0$ gives the optimal $S$ when
$$
n=\frac{n}{S\ln 2} \iff S=\frac{1}{\ln{2}}
$$
There's some constant factor involved in this but i think not that impt.


>not needed since we needed to derive one from practical results instead












---