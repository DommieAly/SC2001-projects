#!/usr/bin/env python3
"""Create the figures required by Parts (c) and (d) from the CSV results."""

import argparse
import csv
import math
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


def fit_two_term_model(observed, first_term, second_term):
    """Fit y = a*x1 + b*x2 using ordinary least squares."""
    x1_x1 = sum(value * value for value in first_term)
    x2_x2 = sum(value * value for value in second_term)
    x1_x2 = sum(x1 * x2 for x1, x2 in zip(first_term, second_term))
    x1_y = sum(x1 * y for x1, y in zip(first_term, observed))
    x2_y = sum(x2 * y for x2, y in zip(second_term, observed))
    determinant = x1_x1 * x2_x2 - x1_x2 * x1_x2

    if math.isclose(determinant, 0.0):
        raise ValueError("The theoretical terms are linearly dependent.")

    first_coefficient = (x1_y * x2_x2 - x2_y * x1_x2) / determinant
    second_coefficient = (x2_y * x1_x1 - x1_y * x1_x2) / determinant
    return first_coefficient, second_coefficient


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
    s_values = [row["s"] for row in rows]
    comparisons = [row["mean_key_comparisons"] for row in rows]
    deviations = [row["stddev_key_comparisons"] for row in rows]
    merge_terms = [row["merge_growth_proxy"] for row in rows]
    insertion_terms = [row["insertion_growth_proxy"] for row in rows]
    merge_weight, insertion_weight = fit_two_term_model(
        comparisons, merge_terms, insertion_terms
    )
    fitted_theory = [
        merge_weight * merge + insertion_weight * insertion
        for merge, insertion in zip(merge_terms, insertion_terms)
    ]

    figure, (full_axis, zoom_axis) = plt.subplots(1, 2, figsize=(13, 5))
    for axis in (full_axis, zoom_axis):
        axis.errorbar(
            s_values,
            comparisons,
            yerr=deviations,
            marker="o",
            markersize=3,
            capsize=2,
            label="Empirical mean comparisons",
        )
        axis.plot(
            s_values,
            fitted_theory,
            linestyle="--",
            label=r"Fitted $a\,n\log_2(n/S) + b\,nS$ trend",
        )
        axis.set_xlabel("Threshold S")
        axis.set_ylabel("Number of key comparisons")

    full_axis.set_title("Full range")
    full_axis.legend()
    zoom_axis.set_xlim(0, 200)
    zoom_indices = [index for index, s in enumerate(s_values) if s <= 200]
    zoom_minimum = min(
        min(comparisons[index] - deviations[index], fitted_theory[index])
        for index in zoom_indices
    )
    zoom_maximum = max(
        max(comparisons[index] + deviations[index], fitted_theory[index])
        for index in zoom_indices
    )
    zoom_padding = (zoom_maximum - zoom_minimum) * 0.08
    zoom_axis.set_ylim(
        zoom_minimum - zoom_padding,
        zoom_maximum + zoom_padding,
    )
    zoom_axis.set_title("Detail for S ≤ 200")
    figure.suptitle(
        f"Part (c)(ii): Comparisons vs S, fixed n = {int(rows[0]['n']):,}"
    )
    figure.tight_layout()
    figure.savefig(output_directory / "part_ii_vary_s.png", dpi=200)
    plt.close(figure)


def plot_part_c_iii(results_directory, output_directory):
    all_rows = read_csv(results_directory / "part_iii_all_candidates.csv")
    optimal_rows = read_csv(results_directory / "part_iii_optimal_s.csv")
    rows_by_size = defaultdict(list)
    for row in all_rows:
        rows_by_size[int(row["n"])].append(row)

    figure, (full_time_axis, zoom_time_axis, optimal_axis) = plt.subplots(
        1, 3, figsize=(18, 5)
    )
    zoom_values = []

    for n, rows in sorted(rows_by_size.items()):
        rows.sort(key=lambda row: row["s"])
        minimum_time = min(row["median_time_ms"] for row in rows)
        s_values = [row["s"] for row in rows]
        relative_times = [row["median_time_ms"] / minimum_time for row in rows]
        for axis in (full_time_axis, zoom_time_axis):
            axis.plot(
                s_values,
                relative_times,
                marker="o",
                markersize=4,
                label=f"n = {n:,}",
            )
        zoom_values.extend(
            time for s, time in zip(s_values, relative_times) if s <= 200
        )

    for axis in (full_time_axis, zoom_time_axis):
        axis.axhline(1.0, color="black", linewidth=0.8, linestyle="--")
        axis.set_xlabel("Threshold S")
        axis.set_ylabel("Median time / minimum median time")

    full_time_axis.set_title("Full candidate range")
    full_time_axis.legend()
    zoom_time_axis.set_xlim(0, 200)
    zoom_time_axis.set_ylim(0.98, max(zoom_values) * 1.03)
    zoom_time_axis.set_title("Detail for S ≤ 200")

    sizes = [row["n"] for row in optimal_rows]
    optimal_axis.plot(
        sizes,
        [row["best_s_by_time"] for row in optimal_rows],
        marker="o",
        label="Best S by median runtime",
    )
    optimal_axis.plot(
        sizes,
        [row["best_s_by_key_comparisons"] for row in optimal_rows],
        marker="s",
        linestyle="--",
        label="Best S by comparisons",
    )
    optimal_axis.set_xscale("log")
    optimal_axis.set_xlabel("Input size n (log scale)")
    optimal_axis.set_ylabel("Best threshold S")
    optimal_axis.set_title("Selected S for each input size")
    optimal_axis.legend()

    figure.suptitle("Part (c)(iii): Determining an optimal S")
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
