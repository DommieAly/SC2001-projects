#!/usr/bin/env python3
"""Create the figures for Parts (a), (b) and (c) from the CSV results."""

import argparse
import csv
import math
from pathlib import Path

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt

# Fixed categorical order, so each implementation keeps its colour in every figure.
ALGORITHM_COLOURS = {
    "Matrix + array (a)": "#2a78d6",
    "Lists + heap (b)": "#eb6834",
    "Matrix + heap": "#1baf7a",
    "Lists + array": "#eda100",
}
SERIES_COLOURS = ["#2a78d6", "#eb6834", "#1baf7a", "#eda100"]
WEIGHT_STYLES = {"random": "-", "worst_case": "--"}
WEIGHT_NAMES = {"random": "random weights", "worst_case": "worst-case weights"}
# One blue, light to dark, for the ordered vertex counts of part (c).
VERTEX_COUNT_BLUES = ["#86b6ef", "#3987e5", "#1c5cab", "#0d366b"]
REFERENCE_COLOUR = "0.45"


def parse_csv_value(value):
    try:
        return float(value)
    except ValueError:
        return value


def read_csv(path):
    with path.open(newline="", encoding="utf-8") as file:
        return [
            {key: parse_csv_value(value) for key, value in row.items()}
            for row in csv.DictReader(file)
        ]


def rows_where(rows, **conditions):
    return [
        row for row in rows
        if all(row[key] == value for key, value in conditions.items())
    ]


def log_scale_fit(observed, theoretical):
    """Fit y = scale * x by the geometric mean of y / x.

    On a log-log plot this centres the reference line on the data, instead of
    letting the largest values decide the fit as least squares would.
    """
    logs = [math.log(y / x) for y, x in zip(observed, theoretical)]
    return math.exp(sum(logs) / len(logs))


def harmonic(k):
    return sum(1.0 / i for i in range(1, int(round(k)) + 1))


def heap_term(row):
    """(V + E) log2 V, the part (b) bound without its constant."""
    return (row["v"] + row["e"]) * math.log2(row["v"])


def key_comparisons(row):
    return row["mean_queue_comparisons"] + row["mean_relaxations"]


def style_axis(axis):
    axis.grid(alpha=0.25)
    axis.spines["top"].set_visible(False)
    axis.spines["right"].set_visible(False)


def add_density_axis(axis, vertex_count):
    density_axis = axis.secondary_xaxis(
        "top",
        functions=(
            lambda x: x / (vertex_count * (vertex_count - 1)),
            lambda d: d * vertex_count * (vertex_count - 1)))
    density_axis.set_xlabel("Density E / V(V-1)")


def plot_part_a_vary_v(results_directory, output_directory):
    rows = read_csv(results_directory / "part_a_vary_v.csv")
    figure, (time_axis, count_axis) = plt.subplots(1, 2, figsize=(13, 5))
    colours = {"sparse": SERIES_COLOURS[0], "complete": SERIES_COLOURS[1]}
    names = {"sparse": "E = 10V", "complete": "complete, E = V(V-1)"}

    for family in ("sparse", "complete"):
        family_rows = rows_where(rows, family=family)
        v = [row["v"] for row in family_rows]
        times = [row["median_cpu_time_ms"] for row in family_rows]
        scale = log_scale_fit(times, [n * n for n in v])
        time_axis.plot(
            v, times, marker="o", color=colours[family], linewidth=2, label=names[family])
        time_axis.plot(
            v, [scale * n * n for n in v], linestyle=":", color=colours[family],
            linewidth=1.5, label=f"fitted c·V², {names[family]}")
        count_axis.plot(
            v, [row["mean_relaxations"] for row in family_rows], marker="s",
            markerfacecolor="white", markersize=8, color=colours[family], linewidth=2,
            label=f"relaxations = E, {names[family]}")

    complete = rows_where(rows, family="complete")
    v = [row["v"] for row in complete]
    count_axis.plot(
        v, [row["mean_entries_scanned"] for row in complete], marker="o",
        color=SERIES_COLOURS[2], linewidth=2, label="matrix cells read (both families)")
    count_axis.plot(
        v, [row["mean_queue_comparisons"] for row in complete], marker="^",
        color=SERIES_COLOURS[3], linewidth=2, label="array-queue comparisons (both families)")
    count_axis.plot(v, [n * n for n in v], linestyle=":", color=REFERENCE_COLOUR, label="V²")
    count_axis.plot(
        v, [n * (n - 1) / 2 for n in v], linestyle="--", color=REFERENCE_COLOUR,
        label="V(V-1)/2")

    for axis in (time_axis, count_axis):
        axis.set_xscale("log")
        axis.set_yscale("log")
        axis.set_xlabel("Number of vertices V (log scale)")
        style_axis(axis)
    time_axis.set_ylabel("Median CPU time (ms, log scale)")
    time_axis.set_title("Running time grows as V², sparse or complete")
    time_axis.legend(fontsize=8)
    count_axis.set_ylabel("Operations per run (log scale)")
    count_axis.set_title("Cells read and queue comparisons do not depend on E")
    count_axis.legend(fontsize=8)

    figure.suptitle("Part (a): adjacency matrix + array queue, varying V (random weights)")
    figure.tight_layout(rect=(0, 0, 1, 0.94), w_pad=3.0)
    figure.savefig(output_directory / "part_a_vary_v.png", dpi=200)
    plt.close(figure)


def plot_part_a_vary_e(results_directory, output_directory):
    rows = read_csv(results_directory / "part_a_vary_e.csv")
    vertex_count = int(rows[0]["v"])
    figure, (time_axis, count_axis) = plt.subplots(1, 2, figsize=(13, 5))

    for weights in ("random", "worst_case"):
        weight_rows = rows_where(rows, weights=weights)
        time_axis.plot(
            [row["e"] for row in weight_rows], [row["median_cpu_time_ms"] for row in weight_rows],
            marker="o", linestyle=WEIGHT_STYLES[weights],
            color=ALGORITHM_COLOURS["Matrix + array (a)"], linewidth=2,
            label=WEIGHT_NAMES[weights])
        count_axis.plot(
            [row["e"] for row in weight_rows], [row["mean_decrease_keys"] for row in weight_rows],
            marker="o", linestyle=WEIGHT_STYLES[weights], color=SERIES_COLOURS[1],
            linewidth=2, label=f"decrease-keys, {WEIGHT_NAMES[weights]}")

    random_rows = rows_where(rows, weights="random")
    e = [row["e"] for row in random_rows]
    count_axis.plot(
        e, [row["mean_entries_scanned"] for row in random_rows], marker="o",
        color=SERIES_COLOURS[2], linewidth=2, label="matrix cells read = V²")
    count_axis.plot(
        e, [row["mean_queue_comparisons"] for row in random_rows], marker="^",
        color=SERIES_COLOURS[3], linewidth=2, label="array-queue comparisons = V(V-1)/2")
    count_axis.plot(
        e, [row["mean_relaxations"] for row in random_rows], marker="s",
        color=SERIES_COLOURS[0], linewidth=2, label="relaxations = E")

    for axis in (time_axis, count_axis):
        axis.set_xscale("log")
        axis.set_xlabel("Number of edges E (log scale)")
        add_density_axis(axis, vertex_count)
        style_axis(axis)
    time_axis.set_ylim(bottom=0)
    time_axis.set_ylabel("Median CPU time (ms)")
    time_axis.set_title(
        "Random weights: flat in E. Worst-case weights: one decrease-key per forward edge", pad=12,
        fontsize=10)
    time_axis.legend(fontsize=8)
    count_axis.set_yscale("log")
    count_axis.set_ylabel("Operations per run (log scale)")
    count_axis.set_title("Only the E-dependent work grows, and it stays ≤ V²", pad=12)
    count_axis.legend(fontsize=7, loc="upper left", bbox_to_anchor=(0.0, 0.85))

    figure.suptitle(
        f"Part (a): adjacency matrix + array queue, varying E at V = {vertex_count:,}")
    figure.tight_layout(rect=(0, 0, 1, 0.95), w_pad=3.0)
    figure.savefig(output_directory / "part_a_vary_e.png", dpi=200)
    plt.close(figure)


def plot_part_a_branch_control(results_directory, output_directory):
    rows = read_csv(results_directory / "part_a_branch_control.csv")
    vertex_count = int(rows[0]["v"])
    density = [row["density"] for row in rows]

    figure, axis = plt.subplots(figsize=(8, 5))
    axis.plot(
        density, [row["median_cpu_time_ms_no_if"] for row in rows], marker="o",
        color=SERIES_COLOURS[0], linewidth=2, label="shipped code: no if on the edge test")
    axis.plot(
        density, [row["median_cpu_time_ms_separate_if"] for row in rows], marker="s",
        color=SERIES_COLOURS[1], linewidth=2, label="control: if (there is an edge) { ... }")
    axis.set_ylim(bottom=0)
    axis.set_xlabel("Density E / V(V-1)")
    axis.set_ylabel("Median CPU time (ms)")
    axis.set_title("Same work, same graphs: only the if-statement differs")
    axis.legend(fontsize=9)
    style_axis(axis)
    figure.suptitle(f"Part (a) control: the edge test and the CPU's guesses (V = {vertex_count:,})")
    figure.tight_layout(rect=(0, 0, 1, 0.94))
    figure.savefig(output_directory / "part_a_branch_control.png", dpi=200)
    plt.close(figure)


def plot_part_b_vary_v(results_directory, output_directory):
    rows = read_csv(results_directory / "part_b_vary_v.csv")
    out_degree = rows[0]["e"] / rows[0]["v"]
    figure, (time_axis, ratio_axis, cost_axis) = plt.subplots(1, 3, figsize=(18, 5))
    colour = ALGORITHM_COLOURS["Lists + heap (b)"]

    for weights in ("random", "worst_case"):
        weight_rows = rows_where(rows, weights=weights)
        v = [row["v"] for row in weight_rows]
        times = [row["median_cpu_time_ms"] for row in weight_rows]
        style = WEIGHT_STYLES[weights]

        time_axis.plot(
            v, times, marker="o", linestyle=style, color=colour, linewidth=2,
            label=WEIGHT_NAMES[weights])
        ratio_axis.plot(
            v, [key_comparisons(row) / heap_term(row) for row in weight_rows],
            marker="o", linestyle=style, color=SERIES_COLOURS[0], linewidth=2,
            label=f"key comparisons / ((V+E) log₂V), {WEIGHT_NAMES[weights]}")
        ratio_axis.plot(
            v, [row["mean_decrease_keys"] / row["e"] for row in weight_rows],
            marker="s", linestyle=style, color=SERIES_COLOURS[2], linewidth=2,
            label=f"decrease-keys / E, {WEIGHT_NAMES[weights]}")
        cost_axis.plot(
            v, [row["median_cpu_time_ms"] * 1e6 / key_comparisons(row) for row in weight_rows],
            marker="o", linestyle=style, color=colour, linewidth=2, label=WEIGHT_NAMES[weights])

    worst = rows_where(rows, weights="worst_case")
    v = [row["v"] for row in worst]
    term = [heap_term(row) for row in worst]
    scale = log_scale_fit([row["median_cpu_time_ms"] for row in worst], term)
    time_axis.plot(
        v, [scale * x for x in term], linestyle=":", color=REFERENCE_COLOUR,
        linewidth=1.5, label="fitted c·(V+E) log₂V")

    time_axis.set_xscale("log")
    time_axis.set_yscale("log")
    time_axis.set_ylabel("Median CPU time (ms, log scale)")
    time_axis.set_title("Running time")
    time_axis.legend(fontsize=8)
    ratio_axis.set_xscale("log")
    ratio_axis.set_ylim(bottom=0)
    ratio_axis.set_ylabel("Measured count / theoretical term")
    ratio_axis.set_title("Counts divided by their theoretical terms stay nearly constant")
    ratio_axis.legend(fontsize=7)
    cost_axis.set_xscale("log")
    cost_axis.set_ylim(bottom=0)
    cost_axis.set_ylabel("Nanoseconds per key comparison")
    cost_axis.set_title("Cost per operation rises once data outgrows the cache")
    cost_axis.legend(fontsize=8)
    for axis in (time_axis, ratio_axis, cost_axis):
        axis.set_xlabel("Number of vertices V (log scale)")
        style_axis(axis)

    figure.suptitle(
        f"Part (b): adjacency lists + min-heap, varying V with E = {out_degree:.0f}V")
    figure.tight_layout(rect=(0, 0, 1, 0.94), w_pad=3.0)
    figure.savefig(output_directory / "part_b_vary_v.png", dpi=200)
    plt.close(figure)


def plot_part_b_vary_e(results_directory, output_directory):
    rows = read_csv(results_directory / "part_b_vary_e.csv")
    vertex_count = int(rows[0]["v"])
    figure, (time_axis, count_axis) = plt.subplots(1, 2, figsize=(13, 5))
    colour = ALGORITHM_COLOURS["Lists + heap (b)"]

    for weights in ("random", "worst_case"):
        weight_rows = rows_where(rows, weights=weights)
        e = [row["e"] for row in weight_rows]
        style = WEIGHT_STYLES[weights]
        time_axis.plot(
            e, [row["median_cpu_time_ms"] for row in weight_rows], marker="o",
            linestyle=style, color=colour, linewidth=2, label=WEIGHT_NAMES[weights])
        count_axis.plot(
            e, [row["mean_decrease_keys"] for row in weight_rows], marker="o",
            linestyle=style, color=SERIES_COLOURS[2], linewidth=2,
            label=f"decrease-keys, {WEIGHT_NAMES[weights]}")
        count_axis.plot(
            e, [row["mean_queue_comparisons"] for row in weight_rows], marker="^",
            linestyle=style, color=SERIES_COLOURS[3], linewidth=2,
            label=f"heap comparisons, {WEIGHT_NAMES[weights]}")

    worst = rows_where(rows, weights="worst_case")
    e = [row["e"] for row in worst]
    term = [heap_term(row) for row in worst]
    scale = log_scale_fit([row["median_cpu_time_ms"] for row in worst], term)
    time_axis.plot(
        e, [scale * x for x in term], linestyle=":", color=REFERENCE_COLOUR,
        linewidth=1.5, label="fitted c·(V+E) log₂V")
    count_axis.plot(
        e, [vertex_count * harmonic(x / vertex_count) for x in e], linestyle="--",
        color=REFERENCE_COLOUR, label="V·H(E/V): bound on expected decrease-keys, random weights")
    count_axis.plot(e, [x / 2 for x in e], linestyle=":", color=REFERENCE_COLOUR, label="E / 2")

    for axis in (time_axis, count_axis):
        axis.set_xscale("log")
        axis.set_yscale("log")
        axis.set_xlabel("Number of edges E (log scale)")
        add_density_axis(axis, vertex_count)
        style_axis(axis)
    time_axis.set_ylabel("Median CPU time (ms, log scale)")
    time_axis.set_title("Running time", pad=12)
    time_axis.legend(fontsize=8)
    count_axis.set_ylabel("Operations per run (log scale)")
    count_axis.set_ylim(top=count_axis.get_ylim()[1] * 300)
    count_axis.set_title("Random weights: few decrease-keys. Worst case: one per forward edge",
                         pad=12, fontsize=10)
    count_axis.legend(fontsize=7)

    figure.suptitle(
        f"Part (b): adjacency lists + min-heap, varying E at V = {vertex_count:,}")
    figure.tight_layout(rect=(0, 0, 1, 0.95), w_pad=3.0)
    figure.savefig(output_directory / "part_b_vary_e.png", dpi=200)
    plt.close(figure)


def plot_part_c(results_directory, output_directory):
    rows = read_csv(results_directory / "part_c_comparison.csv")
    crossovers = read_csv(results_directory / "part_c_crossover.csv")
    vertex_counts = sorted({int(row["v"]) for row in rows})
    largest = vertex_counts[-1]
    weightings = [w for w in ("random", "worst_case") if rows_where(rows, weights=w)]

    figure, axes = plt.subplots(
        len(weightings), 2, figsize=(13, 5 * len(weightings)), squeeze=False)
    for weights, (ratio_axis, time_axis) in zip(weightings, axes):
        for colour, v in zip(VERTEX_COUNT_BLUES[-len(vertex_counts):], vertex_counts):
            heap_rows = rows_where(rows, weights=weights, algorithm="Lists + heap (b)", v=v)
            ratio_axis.plot(
                [row["density"] for row in heap_rows],
                [row["median_ratio_to_a"] for row in heap_rows],
                marker="o", color=colour, linewidth=2, label=f"V = {v:,}")
            for crossover in rows_where(crossovers, weights=weights, v=v):
                if isinstance(crossover["crossover_density"], float):
                    ratio_axis.plot(
                        crossover["crossover_density"], 1.0, marker="D", markersize=9,
                        color=colour, markeredgecolor="white")
        ratio_axis.axhline(1.0, color=REFERENCE_COLOUR, linestyle=":", linewidth=1.2)
        ratio_axis.set_xscale("log")
        ratio_axis.set_yscale("log")
        ratio_axis.set_xlabel("Density E / V(V-1) (log scale)")
        ratio_axis.set_ylabel("Time of (b) / time of (a), same graph (log scale)")
        ratio_axis.set_title(f"{WEIGHT_NAMES[weights].capitalize()}: below 1 means (b) is faster")
        ratio_axis.legend(fontsize=8)
        style_axis(ratio_axis)

        for algorithm, colour in ALGORITHM_COLOURS.items():
            algorithm_rows = rows_where(rows, weights=weights, algorithm=algorithm, v=largest)
            time_axis.plot(
                [row["density"] for row in algorithm_rows],
                [row["median_cpu_time_ms"] for row in algorithm_rows],
                marker="o", color=colour, linewidth=2, label=algorithm)
        time_axis.set_xscale("log")
        time_axis.set_yscale("log")
        time_axis.set_xlabel("Density E / V(V-1) (log scale)")
        time_axis.set_ylabel("Median CPU time (ms, log scale)")
        time_axis.set_title(
            f"{WEIGHT_NAMES[weights].capitalize()}, V = {largest:,}: all four combinations")
        time_axis.legend(fontsize=8)
        style_axis(time_axis)

    figure.suptitle("Part (c): matrix + array (a) against lists + heap (b) on identical graphs")
    figure.tight_layout(rect=(0, 0, 1, 0.97), w_pad=3.0, h_pad=2.5)
    figure.savefig(output_directory / "part_c_comparison.png", dpi=200)
    plt.close(figure)


def main():
    parser = argparse.ArgumentParser(
        description="Plot the CSV files produced by ExperimentRunner."
    )
    parser.add_argument(
        "results_directory",
        nargs="?",
        type=Path,
        default=Path(__file__).resolve().parent.parent / "results",
        help="directory containing the experiment CSV files (default: results)",
    )
    parser.add_argument(
        "--output-directory",
        type=Path,
        default=Path(__file__).resolve().parent.parent / "plots",
        help="directory for the generated PNG files (default: repository plots/)",
    )
    arguments = parser.parse_args()
    arguments.output_directory.mkdir(parents=True, exist_ok=True)
    results = arguments.results_directory
    output = arguments.output_directory

    plots = [
        ("part_a_vary_v.csv", plot_part_a_vary_v),
        ("part_a_vary_e.csv", plot_part_a_vary_e),
        ("part_a_branch_control.csv", plot_part_a_branch_control),
        ("part_b_vary_v.csv", plot_part_b_vary_v),
        ("part_b_vary_e.csv", plot_part_b_vary_e),
        ("part_c_comparison.csv", plot_part_c),
    ]
    for csv_name, plot in plots:
        if (results / csv_name).exists():
            plot(results, output)
        else:
            print(f"Skipping {csv_name}: not found in {results}")
    print(f"Plots saved in: {output.resolve()}")


if __name__ == "__main__":
    main()
