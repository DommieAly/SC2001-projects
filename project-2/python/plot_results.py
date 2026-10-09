#!/usr/bin/env python3
"""Create the figures for Parts (a), (b) and (c) from the CSV results.

Parts (a) and (b) each get one figure per sub-experiment: (i) varying V,
(ii) varying E with random weights, and (iii) best, average and worst case.
"""

import argparse
import csv
import math
from pathlib import Path

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib.ticker import FuncFormatter, NullFormatter

# Fixed categorical order, so each implementation keeps its colour in every figure.
ALGORITHM_COLOURS = {
    "Matrix + array (a)": "#2a78d6",
    "Lists + heap (b)": "#eb6834",
    "Matrix + heap": "#1baf7a",
    "Lists + array": "#eda100",
}
SERIES_COLOURS = ["#2a78d6", "#eb6834", "#1baf7a", "#eda100"]
WEIGHT_NAMES = {
    "uniform": "uniform weights",
    "random": "random weights",
    "worst_case": "worst-case weights",
}
# The three weightings of sub-experiment (iii), from best to worst case.
CASE_ORDER = ("uniform", "random", "worst_case")
CASE_NAMES = {
    "uniform": "best case: uniform weights",
    "random": "average case: random weights",
    "worst_case": "worst case: worst-case weights",
}
CASE_MARKERS = {"uniform": "s", "random": "o", "worst_case": "^"}
# The hue of each part's implementation, light to dark from the best to the worst case.
CASE_SHADES = {
    "Matrix + array (a)": {"uniform": "#86b6ef", "random": "#2a78d6", "worst_case": "#0d366b"},
    "Lists + heap (b)": {"uniform": "#f0956a", "random": "#eb6834", "worst_case": "#93380f"},
}
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


def column(rows, key):
    return [row[key] for row in rows]


def log_scale_fit(observed, theoretical):
    """Fit y = scale * x by the geometric mean of y / x.

    On a log-log plot this centres the reference line on the data, instead of
    letting the largest values decide the fit as least squares would.
    """
    logs = [math.log(y / x) for y, x in zip(observed, theoretical)]
    return math.exp(sum(logs) / len(logs))


def linear_fit(x_values, y_values):
    """Least-squares fit of y = intercept + slope * x."""
    count = len(x_values)
    mean_x = sum(x_values) / count
    mean_y = sum(y_values) / count
    slope = (
        sum((x - mean_x) * (y - mean_y) for x, y in zip(x_values, y_values))
        / sum((x - mean_x) ** 2 for x in x_values))
    return mean_y - slope * mean_x, slope


def relative_linear_fit(x_values, y_values):
    """Fit y = intercept + slope * x by least squares on the relative error.

    Each point is weighted by 1 / y^2, so a point at 0.1 ms counts as much as
    one at 100 ms, which suits data spanning several orders of magnitude.
    """
    weights = [1.0 / (y * y) for y in y_values]
    s0 = sum(weights)
    s1 = sum(w * x for w, x in zip(weights, x_values))
    s2 = sum(w * x * x for w, x in zip(weights, x_values))
    t0 = sum(w * y for w, y in zip(weights, y_values))
    t1 = sum(w * x * y for w, x, y in zip(weights, x_values, y_values))
    slope = (s0 * t1 - s1 * t0) / (s0 * s2 - s1 * s1)
    return (t0 - slope * s1) / s0, slope


def harmonic(k):
    return sum(1.0 / i for i in range(1, int(round(k)) + 1))


def heap_term(row):
    """(V + E) log2 V, the part (b) bound without its constant."""
    return (row["v"] + row["e"]) * math.log2(row["v"])


def forward_edges(e, vertex_count):
    """Expected forward edges, so worst-case decrease-keys: (V-1)(E+V)/(2V), about (E+V)/2 - 1.

    The V - 1 cycle edges all point forward, and a non-cycle edge does with
    probability (V - 1) / (2V), just under one half.
    """
    return (vertex_count - 1) * (e + vertex_count) / (2 * vertex_count)


def style_axis(axis):
    axis.grid(alpha=0.25)
    axis.spines["top"].set_visible(False)
    axis.spines["right"].set_visible(False)


def label_vertex_axis(axis, vertex_counts):
    """Label every measured V on a log x-axis, with thousands separators."""
    axis.set_xticks(vertex_counts)
    axis.xaxis.set_major_formatter(FuncFormatter(lambda x, _: f"{x:,.0f}"))
    axis.xaxis.set_minor_formatter(NullFormatter())
    axis.tick_params(axis="x", labelrotation=90, labelsize=8)


def zero_based_ylim(axis, values):
    """Start the y-axis at 0 with room above the largest value, so no marker is clipped."""
    axis.set_ylim(0, 1.1 * max(values))


def add_density_axis(axis, vertex_count):
    density_axis = axis.secondary_xaxis(
        "top",
        functions=(
            lambda x: x / (vertex_count * (vertex_count - 1)),
            lambda d: d * vertex_count * (vertex_count - 1)))
    density_axis.set_xlabel("Density E / V(V-1)")


def plot_case_times(axis, rows, algorithm):
    """Median time against E for each weighting, light to dark from best to worst case."""
    for weights in CASE_ORDER:
        weight_rows = rows_where(rows, weights=weights)
        axis.plot(
            column(weight_rows, "e"), column(weight_rows, "median_cpu_time_ms"),
            marker=CASE_MARKERS[weights], color=CASE_SHADES[algorithm][weights],
            linewidth=2, label=CASE_NAMES[weights])


def plot_case_decrease_keys(axis, rows, algorithm, vertex_count):
    """Decrease-keys against E for each weighting, with the three theoretical counts."""
    e = column(rows_where(rows, weights="random"), "e")
    axis.plot(
        e, [forward_edges(x, vertex_count) for x in e], linestyle="--", color=REFERENCE_COLOUR,
        linewidth=1.5, label="(E+V)/2 − 1: one per forward edge")
    axis.plot(
        e, [vertex_count * harmonic(x / vertex_count) for x in e], linestyle="-.",
        color=REFERENCE_COLOUR, linewidth=1.5, label="V·H(E/V): bound on the random-weight mean")
    axis.plot(
        e, [vertex_count - 1 for _ in e], linestyle=":", color=REFERENCE_COLOUR,
        linewidth=1.5, label="V − 1: the fewest possible")
    for weights in CASE_ORDER:
        weight_rows = rows_where(rows, weights=weights)
        axis.plot(
            column(weight_rows, "e"), column(weight_rows, "mean_decrease_keys"),
            marker=CASE_MARKERS[weights], color=CASE_SHADES[algorithm][weights],
            linewidth=2, label=CASE_NAMES[weights])
    axis.set_xscale("log")
    axis.set_yscale("log")
    axis.set_ylabel("Decrease-keys per run (log scale)")


def plot_part_a_vary_v(results_directory, output_directory):
    rows = read_csv(results_directory / "part_a_vary_v.csv")
    edge_count = int(rows[0]["e"])
    v = column(rows, "v")
    times = column(rows, "median_cpu_time_ms")
    figure, (time_axis, count_axis) = plt.subplots(1, 2, figsize=(13, 5.5))

    scale = log_scale_fit(times, [n * n for n in v])
    time_axis.plot(
        v, [scale * n * n for n in v], linestyle=":", color=REFERENCE_COLOUR,
        linewidth=1.5, label="fitted c·V²")
    time_axis.plot(
        v, times, marker="o", color=ALGORITHM_COLOURS["Matrix + array (a)"], linewidth=2,
        label=f"measured, E = {edge_count:,}")

    count_axis.plot(
        v, column(rows, "mean_entries_scanned"), marker="o", color=SERIES_COLOURS[2],
        linewidth=2, label="matrix cells read = V²")
    count_axis.plot(
        v, column(rows, "mean_queue_comparisons"), marker="^", color=SERIES_COLOURS[3],
        linewidth=2, label="array-queue comparisons = V(V-1)/2")
    count_axis.plot(
        v, column(rows, "mean_relaxations"), marker="s", color=SERIES_COLOURS[0],
        linewidth=2, label=f"relaxations = E = {edge_count:,}")
    count_axis.plot(
        v, column(rows, "mean_decrease_keys"), marker="D", color=SERIES_COLOURS[1],
        linewidth=2, label="decrease-keys")

    for axis in (time_axis, count_axis):
        axis.set_xscale("log")
        axis.set_yscale("log")
        label_vertex_axis(axis, v)
        axis.set_xlabel("Number of vertices V (log scale)")
        style_axis(axis)
    time_axis.set_ylabel("Median CPU time (ms, log scale)")
    time_axis.set_title("Running time grows as V²")
    time_axis.legend(fontsize=9)
    count_axis.set_ylabel("Operations per run (log scale)")
    count_axis.set_title("The V² terms grow; relaxations stay at E")
    count_axis.legend(fontsize=8)

    figure.suptitle(
        f"Part (a)(i): adjacency matrix + array queue, varying V at E = {edge_count:,} "
        "(random weights)")
    figure.tight_layout(rect=(0, 0, 1, 0.94), w_pad=3.0)
    figure.savefig(output_directory / "part_a_vary_v.png", dpi=200)
    plt.close(figure)


def plot_part_a_vary_e(results_directory, output_directory):
    rows = rows_where(read_csv(results_directory / "part_a_vary_e.csv"), weights="random")
    vertex_count = int(rows[0]["v"])
    e = column(rows, "e")
    times = column(rows, "median_cpu_time_ms")
    spread = max(times) / min(times) - 1
    figure, (time_axis, count_axis) = plt.subplots(1, 2, figsize=(13, 5))

    time_axis.plot(
        e, times, marker="o", color=ALGORITHM_COLOURS["Matrix + array (a)"], linewidth=2,
        label="random weights")
    count_axis.plot(
        e, column(rows, "mean_entries_scanned"), marker="o", color=SERIES_COLOURS[2],
        linewidth=2, label="matrix cells read = V²")
    count_axis.plot(
        e, column(rows, "mean_queue_comparisons"), marker="^", color=SERIES_COLOURS[3],
        linewidth=2, label="array-queue comparisons = V(V-1)/2")
    count_axis.plot(
        e, column(rows, "mean_relaxations"), marker="s", color=SERIES_COLOURS[0],
        linewidth=2, label="relaxations = E")
    count_axis.plot(
        e, column(rows, "mean_decrease_keys"), marker="D", color=SERIES_COLOURS[1],
        linewidth=2, label="decrease-keys")

    for axis in (time_axis, count_axis):
        axis.set_xscale("log")
        axis.set_xlabel("Number of edges E (log scale)")
        add_density_axis(axis, vertex_count)
        style_axis(axis)
    zero_based_ylim(time_axis, times)
    time_axis.set_ylabel("Median CPU time (ms)")
    time_axis.set_title(
        f"The time hardly depends on E: within {spread:.0%} while E grows "
        f"{max(e) / min(e):,.0f}-fold", pad=12, fontsize=11)
    time_axis.legend(fontsize=9, loc="lower right")
    count_axis.set_yscale("log")
    count_axis.set_ylabel("Operations per run (log scale)")
    count_axis.set_title("Only the E terms grow, and they stay ≤ V²", pad=12)
    count_axis.legend(fontsize=8, loc="upper left", bbox_to_anchor=(0.0, 0.86))

    figure.suptitle(
        f"Part (a)(ii): adjacency matrix + array queue, varying E at V = {vertex_count:,} "
        "(random weights)")
    figure.tight_layout(rect=(0, 0, 1, 0.95), w_pad=3.0)
    figure.savefig(output_directory / "part_a_vary_e.png", dpi=200)
    plt.close(figure)


def plot_part_a_cases(results_directory, output_directory):
    rows = read_csv(results_directory / "part_a_vary_e.csv")
    vertex_count = int(rows[0]["v"])
    algorithm = "Matrix + array (a)"
    figure, (time_axis, count_axis) = plt.subplots(1, 2, figsize=(13, 5.5))

    plot_case_times(time_axis, rows, algorithm)
    time_axis.set_xscale("log")
    zero_based_ylim(time_axis, column(rows, "median_cpu_time_ms"))
    time_axis.set_ylabel("Median CPU time (ms)")
    time_axis.set_title("Same Θ(V²) work; the worst case pays for wrong guesses", pad=12)
    time_axis.legend(fontsize=8, loc="upper left")

    plot_case_decrease_keys(count_axis, rows, algorithm, vertex_count)
    count_axis.set_title("Decrease-keys: V − 1, at most V·H(E/V), (E+V)/2 − 1", pad=12)
    count_axis.legend(fontsize=7.5, loc="upper left")

    for axis in (time_axis, count_axis):
        axis.set_xlabel("Number of edges E (log scale)")
        add_density_axis(axis, vertex_count)
        style_axis(axis)

    figure.suptitle(
        f"Part (a)(iii): adjacency matrix + array queue, best, average and worst case "
        f"at V = {vertex_count:,}")
    figure.tight_layout(rect=(0, 0, 1, 0.95), w_pad=3.0)
    figure.savefig(output_directory / "part_a_cases.png", dpi=200)
    plt.close(figure)


def plot_part_a_branch_control(results_directory, output_directory):
    rows = read_csv(results_directory / "part_a_branch_control.csv")
    vertex_count = int(rows[0]["v"])
    density = column(rows, "density")
    no_if = column(rows, "median_cpu_time_ms_no_if")
    separate_if = column(rows, "median_cpu_time_ms_separate_if")

    figure, axis = plt.subplots(figsize=(8, 5))
    axis.plot(
        density, no_if, marker="o", color=SERIES_COLOURS[0], linewidth=2,
        label="shipped code: no if on the edge test")
    axis.plot(
        density, separate_if, marker="s", color=SERIES_COLOURS[1], linewidth=2,
        label="control: if (there is an edge) { ... }")
    zero_based_ylim(axis, no_if + separate_if)
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
    """(b)(i), with (a)(i) on the same axes: both use the same E and the same seeds."""
    rows = rows_where(read_csv(results_directory / "part_b_vary_v.csv"), weights="random")
    edge_count = int(rows[0]["e"])
    v = column(rows, "v")
    times = column(rows, "median_cpu_time_ms")
    heap_comparisons = column(rows, "mean_queue_comparisons")
    figure, (time_axis, count_axis) = plt.subplots(1, 2, figsize=(15, 6))
    all_times = list(times)

    matrix_file = results_directory / "part_a_vary_v.csv"
    if matrix_file.exists():
        matrix_rows = read_csv(matrix_file)
        matrix_v = column(matrix_rows, "v")
        matrix_times = column(matrix_rows, "median_cpu_time_ms")
        all_times += matrix_times
        scale = log_scale_fit(matrix_times, [n * n for n in matrix_v])
        time_axis.plot(
            matrix_v, [scale * n * n for n in matrix_v], linestyle=":", color=REFERENCE_COLOUR,
            linewidth=1.5, label="(a): fitted c·V²")
        time_axis.plot(
            matrix_v, matrix_times, marker="s", color=ALGORITHM_COLOURS["Matrix + array (a)"],
            linewidth=2, label="(a) matrix + array, from part (a)(i)")
    intercept, slope = relative_linear_fit(heap_comparisons, times)
    time_axis.plot(
        v, [intercept + slope * h for h in heap_comparisons], linestyle="--",
        color=REFERENCE_COLOUR, linewidth=1.5,
        label=(f"(b): A + B·(measured heap comparisons), least squares\n"
               f"on relative error: A = {intercept:.2f} ms, B = {slope * 1e6:.1f} ns"))
    time_axis.plot(
        v, times, marker="o", color=ALGORITHM_COLOURS["Lists + heap (b)"], linewidth=2,
        label="(b) lists + heap")

    count_axis.plot(
        v, [n * math.log2(n) for n in v], linestyle=":", color=REFERENCE_COLOUR,
        linewidth=1.5, label="V·log₂V")
    count_axis.plot(
        v, heap_comparisons, marker="^", color=SERIES_COLOURS[3], linewidth=2,
        label="heap comparisons")
    count_axis.plot(
        v, column(rows, "mean_relaxations"), marker="s", color=SERIES_COLOURS[0],
        linewidth=2, label=f"relaxations = E = {edge_count:,}")
    count_axis.plot(
        v, [n * harmonic(edge_count / n) for n in v], linestyle="-.", color=REFERENCE_COLOUR,
        linewidth=1.5, label="V·H(E/V): bound on mean decrease-keys")
    count_axis.plot(
        v, column(rows, "mean_decrease_keys"), marker="o", color=SERIES_COLOURS[2],
        linewidth=2, label="decrease-keys")

    for axis in (time_axis, count_axis):
        axis.set_xscale("log")
        axis.set_yscale("log")
        label_vertex_axis(axis, v)
        axis.set_xlabel("Number of vertices V (log scale)")
        style_axis(axis)
    # Room above the data for the legend.
    time_axis.set_ylim(0.8 * min(all_times), 12 * max(all_times))
    time_axis.set_ylabel("Median CPU time (ms, log scale)")
    time_axis.set_title(f"Same E = {edge_count:,}: (a) grows as V², (b) far more slowly")
    time_axis.legend(fontsize=8, loc="upper left")
    count_axis.set_ylabel("Operations per run (log scale), part (b)")
    count_axis.set_title("(b): heap work grows like V log V; relaxations stay at E")
    count_axis.legend(fontsize=8, loc="upper left")

    figure.suptitle(
        f"Part (b)(i): adjacency lists + min-heap, varying V at E = {edge_count:,} "
        "(random weights), with part (a)(i)")
    figure.tight_layout(rect=(0, 0, 1, 0.94), w_pad=3.0)
    figure.savefig(output_directory / "part_b_vary_v.png", dpi=200)
    plt.close(figure)


def plot_part_b_vary_e(results_directory, output_directory):
    rows = rows_where(read_csv(results_directory / "part_b_vary_e.csv"), weights="random")
    vertex_count = int(rows[0]["v"])
    e = column(rows, "e")
    times = column(rows, "median_cpu_time_ms")
    colour = ALGORITHM_COLOURS["Lists + heap (b)"]
    figure, (time_axis, count_axis) = plt.subplots(1, 2, figsize=(13, 5))

    intercept, slope = linear_fit(e, times)
    fitted = [intercept + slope * x for x in e]
    time_axis.plot(
        e, fitted, linestyle=":", color=REFERENCE_COLOUR, linewidth=1.5,
        label=f"least-squares a + b·E, b = {slope * 1e6:.2f} ns per edge")
    time_axis.plot(e, times, marker="o", color=colour, linewidth=2, label="random weights")

    count_axis.plot(
        e, column(rows, "mean_relaxations"), marker="s", color=SERIES_COLOURS[0],
        linewidth=2, label="list entries read = relaxations = E")
    count_axis.plot(
        e, column(rows, "mean_queue_comparisons"), marker="^", color=SERIES_COLOURS[3],
        linewidth=2, label="heap comparisons")
    count_axis.plot(
        e, [vertex_count * harmonic(x / vertex_count) for x in e], linestyle="-.",
        color=REFERENCE_COLOUR, linewidth=1.5, label="V·H(E/V): bound on mean decrease-keys")
    count_axis.plot(
        e, column(rows, "mean_decrease_keys"), marker="o", color=SERIES_COLOURS[2],
        linewidth=2, label="decrease-keys")

    zero_based_ylim(time_axis, times + fitted)
    time_axis.set_xlim(left=0)
    time_axis.xaxis.set_major_formatter(FuncFormatter(lambda x, _: f"{x / 1e6:g}"))
    time_axis.set_xlabel("Number of edges E (millions)")
    time_axis.set_ylabel("Median CPU time (ms)")
    time_axis.set_title("The running time grows almost linearly in E", pad=12)
    time_axis.legend(fontsize=9, loc="upper left")
    count_axis.set_xscale("log")
    count_axis.set_yscale("log")
    count_axis.set_xlabel("Number of edges E (log scale)")
    count_axis.set_ylabel("Operations per run (log scale)")
    count_axis.set_title("List reads grow with E; the heap work barely does", pad=12)
    count_axis.legend(fontsize=8, loc="upper left")
    for axis in (time_axis, count_axis):
        add_density_axis(axis, vertex_count)
        style_axis(axis)

    figure.suptitle(
        f"Part (b)(ii): adjacency lists + min-heap, varying E at V = {vertex_count:,} "
        "(random weights)")
    figure.tight_layout(rect=(0, 0, 1, 0.95), w_pad=3.0)
    figure.savefig(output_directory / "part_b_vary_e.png", dpi=200)
    plt.close(figure)


def plot_part_b_cases(results_directory, output_directory):
    rows = read_csv(results_directory / "part_b_vary_e.csv")
    vertex_count = int(rows[0]["v"])
    algorithm = "Lists + heap (b)"
    figure, (time_axis, count_axis) = plt.subplots(1, 2, figsize=(13, 5.5))

    worst = rows_where(rows, weights="worst_case")
    term = [heap_term(row) for row in worst]
    scale = log_scale_fit(column(worst, "median_cpu_time_ms"), term)
    time_axis.plot(
        column(worst, "e"), [scale * x for x in term], linestyle=":", color=REFERENCE_COLOUR,
        linewidth=1.5, label="c·(V+E) log₂V, c fitted to all worst-case points")
    plot_case_times(time_axis, rows, algorithm)
    time_axis.set_xscale("log")
    time_axis.set_yscale("log")
    time_axis.set_ylabel("Median CPU time (ms, log scale)")
    time_axis.set_title("Each decrease-key costs up to log V heap levels", pad=12)
    time_axis.legend(fontsize=8, loc="upper left")

    plot_case_decrease_keys(count_axis, rows, algorithm, vertex_count)
    count_axis.set_title("Decrease-keys: V − 1, at most V·H(E/V), (E+V)/2 − 1", pad=12)
    count_axis.legend(fontsize=7.5, loc="upper left")

    for axis in (time_axis, count_axis):
        axis.set_xlabel("Number of edges E (log scale)")
        add_density_axis(axis, vertex_count)
        style_axis(axis)

    figure.suptitle(
        f"Part (b)(iii): adjacency lists + min-heap, best, average and worst case "
        f"at V = {vertex_count:,}")
    figure.tight_layout(rect=(0, 0, 1, 0.95), w_pad=3.0)
    figure.savefig(output_directory / "part_b_cases.png", dpi=200)
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
        ("part_a_vary_e.csv", plot_part_a_cases),
        ("part_a_branch_control.csv", plot_part_a_branch_control),
        ("part_b_vary_v.csv", plot_part_b_vary_v),
        ("part_b_vary_e.csv", plot_part_b_vary_e),
        ("part_b_vary_e.csv", plot_part_b_cases),
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
