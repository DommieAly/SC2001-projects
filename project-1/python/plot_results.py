#!/usr/bin/env python3
"""Create the figures required by Parts (c) and (d) from the CSV results."""

import argparse
import csv
import math
from functools import lru_cache
from collections import defaultdict
from pathlib import Path

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt


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


def fitted_scale(observed, theoretical):
    """Fit y = scale * x so an asymptotic trend can share the data axis."""
    numerator = sum(y * x for y, x in zip(observed, theoretical))
    denominator = sum(x * x for x in theoretical)
    return numerator / denominator


@lru_cache(maxsize=None)
def predicted_comparisons(size, threshold):
    """Expected key comparisons, evaluated on the real recursion tree.

    Merging m elements costs at most m key comparisons, and insertion sort on a
    random array of length L averages L^2 / 4. Walking the actual tree, instead
    of assuming n/S uniform leaves of size S, is what makes this a step
    function: it is flat wherever a range of S drives the recursion to the same
    depth, which is exactly what the measurements do.
    """
    if size <= 1:
        return 0.0
    if size <= threshold:
        return size * size / 4
    left = (size + 1) // 2
    return (
        predicted_comparisons(left, threshold)
        + predicted_comparisons(size - left, threshold)
        + size
    )


def plot_part_c_i(results_directory, output_directory):
    rows = read_csv(results_directory / "part_i_vary_n.csv")
    sizes = [row["n"] for row in rows]
    comparisons = [row["mean_key_comparisons"] for row in rows]
    deviations = [row["stddev_key_comparisons"] for row in rows]
    theoretical = [row["n_log2_n"] for row in rows]
    scale = fitted_scale(comparisons, theoretical)

    figure, axis = plt.subplots(figsize=(8, 5))
    axis.errorbar(
        sizes,
        comparisons,
        yerr=deviations,
        marker="o",
        capsize=3,
        label="Empirical mean comparisons",
    )
    axis.plot(
        sizes,
        [scale * value for value in theoretical],
        linestyle="--",
        label=r"Scaled theoretical $n\log_2 n$",
    )
    axis.set_xscale("log")
    axis.set_yscale("log")
    axis.set_xlabel("Input size n (log scale)")
    axis.set_ylabel("Number of key comparisons (log scale)")
    axis.set_title(
        f"Part (c)(i): Comparisons vs n, fixed S = {int(rows[0]['s'])}"
    )
    axis.legend()
    figure.tight_layout()
    figure.savefig(output_directory / "part_i_vary_n.png", dpi=200)
    plt.close(figure)


def plot_part_c_ii(results_directory, output_directory):
    rows = read_csv(results_directory / "part_ii_vary_s.csv")
    input_size = int(rows[0]["n"])
    s_values = [int(row["s"]) for row in rows]
    comparisons = [row["mean_key_comparisons"] for row in rows]

    # Drawn on every integer S, not only the sampled ones, so that the
    # predicted step edges land where the model actually puts them.
    dense_s = list(range(1, max(s_values) + 1))
    dense_theory = [predicted_comparisons(input_size, s) for s in dense_s]

    figure, (full_axis, zoom_axis) = plt.subplots(1, 2, figsize=(13, 5))
    for axis in (full_axis, zoom_axis):
        axis.plot(
            dense_s,
            dense_theory,
            linewidth=2,
            label="Theory on the real recursion tree, no fitted parameters",
        )
        axis.plot(
            s_values,
            comparisons,
            marker="o",
            markersize=4,
            label="Empirical mean comparisons",
        )
        axis.set_xlabel("Threshold S")
        axis.set_ylabel("Number of key comparisons")
        axis.grid(alpha=0.25)

    full_axis.set_title("Full range")
    full_axis.legend(loc="lower right")

    zoom_limit = 200
    zoom_axis.set_xlim(0, zoom_limit)
    visible = [c for s, c in zip(s_values, comparisons) if s <= zoom_limit]
    visible += [t for s, t in zip(dense_s, dense_theory) if s <= zoom_limit]
    padding = (max(visible) - min(visible)) * 0.08
    zoom_axis.set_ylim(min(visible) - padding, max(visible) + padding)
    zoom_axis.set_title(f"Detail for S \u2264 {zoom_limit}")

    figure.suptitle(
        f"Part (c)(ii): Comparisons vs S, fixed n = {input_size:,}"
    )
    figure.tight_layout()
    figure.savefig(output_directory / "part_ii_vary_s.png", dpi=200)
    plt.close(figure)


def plot_part_c_iii(results_directory, output_directory):
    rows = [
        row
        for row in read_csv(results_directory / "part_iii_candidates.csv")
        if row["role"] == "candidate"
    ]
    rows_by_size = defaultdict(list)
    for row in rows:
        rows_by_size[int(row["n"])].append(row)
    for candidate_rows in rows_by_size.values():
        candidate_rows.sort(key=lambda row: row["leaf_size"])

    figure, (time_axis, criteria_axis) = plt.subplots(1, 2, figsize=(13, 5))

    for n, candidate_rows in sorted(rows_by_size.items()):
        leaf_sizes = [row["leaf_size"] for row in candidate_rows]
        times = [row["median_time_ms"] for row in candidate_rows]
        fastest = min(times)
        time_axis.plot(
            leaf_sizes,
            [time / fastest for time in times],
            marker="o",
            markersize=4,
            label=f"n = {n:,}",
        )

    time_axis.axhline(1.0, color="0.6", linestyle=":", linewidth=1)
    time_axis.set_xscale("log", base=2)
    time_axis.set_xlabel("Leaf size (log scale)")
    time_axis.set_ylabel("Median time / fastest candidate for that n")
    time_axis.set_title("The fastest leaf size barely moves with n")
    time_axis.grid(alpha=0.25)
    time_axis.legend()

    reference_n = 100_000 if 100_000 in rows_by_size else max(rows_by_size)
    reference_rows = rows_by_size[reference_n]
    leaf_sizes = [row["leaf_size"] for row in reference_rows]
    comparisons = [row["mean_key_comparisons"] for row in reference_rows]
    times = [row["median_time_ms"] for row in reference_rows]

    criteria_axis.plot(
        leaf_sizes,
        [value / min(comparisons) for value in comparisons],
        marker="s",
        markersize=4,
        label="Key comparisons",
    )
    criteria_axis.plot(
        leaf_sizes,
        [value / min(times) for value in times],
        marker="o",
        markersize=4,
        label="Running time",
    )
    criteria_axis.axvline(
        leaf_sizes[comparisons.index(min(comparisons))],
        color="C0",
        linestyle=":",
        linewidth=1.2,
    )
    criteria_axis.axvline(
        leaf_sizes[times.index(min(times))],
        color="C1",
        linestyle=":",
        linewidth=1.2,
    )
    criteria_axis.set_xscale("log", base=2)
    criteria_axis.set_yscale("log")
    criteria_axis.set_xlabel("Leaf size (log scale)")
    criteria_axis.set_ylabel("Relative to that criterion's own minimum")
    criteria_axis.set_title(
        f"Comparisons and time disagree (n = {reference_n:,})"
    )
    criteria_axis.grid(alpha=0.25)
    criteria_axis.legend()

    figure.suptitle(
        "Part (c)(iii): one candidate per halving interval of S"
    )
    figure.tight_layout(rect=(0, 0, 1, 0.94), w_pad=4.0)
    figure.savefig(output_directory / "part_iii_optimal_s.png", dpi=200)
    plt.close(figure)


def plot_part_d(results_directory, output_directory):
    rows = read_csv(results_directory / "part_d_comparison.csv")
    algorithm_names = [row["algorithm"] for row in rows]
    key_comparisons = [row["mean_key_comparisons"] for row in rows]
    key_comparison_deviations = [
        row["stddev_key_comparisons"] for row in rows
    ]
    median_cpu_times = [row["median_cpu_time_ms"] for row in rows]
    cpu_time_deviations = [row["stddev_cpu_time_ms"] for row in rows]

    figure, (comparison_axis, time_axis) = plt.subplots(1, 2, figsize=(12, 5))

    comparison_bars = comparison_axis.bar(
        algorithm_names,
        key_comparisons,
        yerr=key_comparison_deviations,
        capsize=5,
    )
    comparison_axis.bar_label(comparison_bars, fmt="%.0f", padding=3)
    comparison_axis.set_ylabel("Mean number of key comparisons")
    comparison_axis.set_title("Key comparisons")
    comparison_axis.ticklabel_format(style="sci", axis="y", scilimits=(0, 0))

    time_bars = time_axis.bar(
        algorithm_names,
        median_cpu_times,
        yerr=cpu_time_deviations,
        capsize=5,
    )
    time_axis.bar_label(time_bars, fmt="%.2f ms", padding=3)
    time_axis.set_ylabel("Median CPU time (ms)")
    time_axis.set_title("CPU time")

    input_size = int(rows[0]["n"])
    hybrid_s = int(rows[1]["s"])
    figure.suptitle(
        "Part (d): Original vs Hybrid Merge Sort "
        f"(n = {input_size:,}, S = {hybrid_s})"
    )
    figure.tight_layout(rect=(0, 0, 1, 0.94), w_pad=4.0)
    figure.savefig(output_directory / "part_d_comparison.png", dpi=200)
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

    plot_part_c_i(arguments.results_directory, arguments.output_directory)
    plot_part_c_ii(arguments.results_directory, arguments.output_directory)
    plot_part_c_iii(arguments.results_directory, arguments.output_directory)
    plot_part_d(arguments.results_directory, arguments.output_directory)
    print(f"Plots saved in: {arguments.output_directory.resolve()}")


if __name__ == "__main__":
    main()
