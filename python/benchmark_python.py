"""Compare the Python sorts; save raw timings and a standalone SVG plot."""

import csv
import gc
from pathlib import Path
from statistics import median
from time import perf_counter, process_time

from array_generator import DEFAULT_SIZES, generate_random_array
from pythonsyn import hybridsort, merge_sort


ROOT = Path(__file__).resolve().parent.parent
OUTPUT = ROOT / "results"
PLOTS = ROOT / "plots"
THRESHOLD = 16
TRIALS = 3


def plot(rows):
    sizes = sorted({r['n'] for r in rows})
    series = {name: [median(r['wall_seconds'] for r in rows
                            if r['n'] == n and r['algorithm'] == name)
                     for n in sizes]
              for name in ('Merge sort', 'Hybrid sort')}
    ymax = max(max(values) for values in series.values()) * 1.15
    x = lambda n: 100 + n / max(sizes) * 850
    y = lambda t: 490 - t / ymax * 340
    svg = ['<svg xmlns="http://www.w3.org/2000/svg" width="1050" height="640" viewBox="0 0 1050 640">',
           '<rect width="1050" height="640" fill="#f8fafc"/>',
           '<g font-family="Arial, sans-serif" fill="#172033">',
           '<text x="60" y="48" font-size="28" font-weight="bold">Python sorting performance</text>',
           '<text x="60" y="78" font-size="16">Median elapsed time of 3 trials • identical inputs • hybrid S = 16</text>']
    for i in range(6):
        t = ymax * i / 5
        yy = y(t)
        svg += [f'<path d="M100 {yy} H950" stroke="#dbe2ea"/>',
                f'<text x="85" y="{yy+5}" text-anchor="end" font-size="14">{t:.1f}</text>']
    for n in (0, 2_000_000, 4_000_000, 6_000_000, 8_000_000, 10_000_000):
        svg.append(f'<text x="{x(n)}" y="518" text-anchor="middle" font-size="14">{n/1e6:g}M</text>')
    svg += ['<text x="525" y="550" text-anchor="middle" font-size="16">Input size (integers)</text>',
            '<text transform="translate(30 330) rotate(-90)" text-anchor="middle" font-size="16">Elapsed sorting time (seconds)</text>']
    for i, (name, values) in enumerate(series.items()):
        color = ('#2563eb', '#e06424')[i]
        points = ' '.join(f'{x(n)},{y(t)}' for n, t in zip(sizes, values))
        svg.append(f'<polyline points="{points}" fill="none" stroke="{color}" stroke-width="3"/>')
        for n, t in zip(sizes, values):
            svg.append(f'<circle cx="{x(n)}" cy="{y(t)}" r="4" fill="{color}"><title>{name}: n={n:,}, {t:.4f} s</title></circle>')
        svg.append(f'<text x="{100+i*370}" y="120" fill="{color}" font-size="17">● {name}: {values[-1]:.2f} s at 10M</text>')
    svg += ['<text x="60" y="593" font-size="14">Values: 1–1,000,000; seeds: 20260910–20260912. Generation, copying and validation excluded.</text>',
            '<text x="60" y="618" font-size="14">Measured locally; lines connect tested sizes. Smaller inputs cluster near the origin on this linear scale.</text>',
            '</g></svg>']
    PLOTS.mkdir(exist_ok=True)
    (PLOTS / 'python_sort_performance.svg').write_text('\n'.join(svg), encoding='utf-8')


def main():
    OUTPUT.mkdir(exist_ok=True)
    rows = []
    # Exercise both implementations before collecting timings.
    small = generate_random_array(1000, 1000, 42)
    assert merge_sort(small.copy()) == sorted(small)
    assert hybridsort(small.copy(), THRESHOLD) == sorted(small)
    with (OUTPUT / 'python_sort_timings.csv').open('w', newline='') as file:
        fields = ['n', 'trial', 'seed', 'algorithm', 'threshold', 'wall_seconds', 'cpu_seconds']
        writer = csv.DictWriter(file, fieldnames=fields)
        writer.writeheader()
        for n in DEFAULT_SIZES:
            for trial in range(TRIALS):
                seed = 20260910 + trial
                original = generate_random_array(n, 1_000_000, seed)
                expected = sorted(original)
                algorithms = [('Merge sort', merge_sort),
                              ('Hybrid sort', lambda a: hybridsort(a, THRESHOLD))]
                if trial % 2:
                    algorithms.reverse()
                for name, sort in algorithms:
                    data = original.copy()
                    gc.collect()
                    cpu_start = process_time()
                    start = perf_counter()
                    result = sort(data)
                    wall = perf_counter() - start
                    cpu = process_time() - cpu_start
                    assert result == expected, f'{name} failed at n={n}'
                    row = dict(n=n, trial=trial+1, seed=seed, algorithm=name,
                               threshold=THRESHOLD if name == 'Hybrid sort' else 1,
                               wall_seconds=wall, cpu_seconds=cpu)
                    rows.append(row)
                    writer.writerow(row)
                    file.flush()
                    print(f'n={n:,} trial={trial+1} {name}: {wall:.3f}s (CPU {cpu:.3f}s)', flush=True)
                    del result, data
                del original, expected
    plot(rows)
    print('Saved results/python_sort_timings.csv and plots/python_sort_performance.svg', flush=True)


if __name__ == '__main__':
    main()
