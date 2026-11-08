#!/usr/bin/env python3
"""Draw the subproblem graph of part (2) from the CSV results.

Vertices sit on a line, largest capacity first, as in the lecture's graph of
fib(6), and the distance between two vertices is the difference of their
capacities, so all edges of one object type have the same span. The script
needs only the standard library: it writes SVG, and on macOS `sips` converts
that to PNG.
"""

import argparse
import csv
import math
import shutil
import subprocess
from pathlib import Path

# The same categorical order as the figures of the other projects.
TYPE_COLOURS = ["#2a78d6", "#eb6834", "#1baf7a"]
# For each object type, which side of the line its edges take (1 is above) and
# the angle on the vertex circle where they leave. Types 0 and 2 share a side
# because that placement gives the fewest edge crossings.
TYPE_SIDE = [1, -1, 1]
TYPE_ANGLE = [math.radians(48), math.radians(55), math.radians(76)]

TEXT = "#1a1a1a"
MUTED = "#5c5c5c"
BASE_CASE_FILL = "#e6e6e6"
FONT = "Helvetica, Arial, sans-serif"

UNIT = 72
RADIUS = 27
MARGIN = 60
LINE_Y = 332
LEGEND_Y = 524
HEIGHT = 552
ARC_HEIGHT = 0.55
ARC_INSET = 0.12
ARROW_LENGTH = 12
ARROW_HALF_WIDTH = 5.5
EDGE_WIDTH = 1.6
OPTIMAL_EDGE_WIDTH = 3.6
PNG_SCALE = 2


def read_rows(path):
    with path.open(newline="", encoding="utf-8") as file:
        return [{key: int(value) for key, value in row.items()} for row in csv.DictReader(file)]


def optimal_edges(root, profits, edges):
    """The edges of the choice the algorithm makes at each step down from the root."""
    chosen = set()
    current = root
    while True:
        winner = next(
            (edge for edge in edges
             if edge["capacity"] == current
             and edge["profit"] + profits[edge["subproblem"]] == profits[current]),
            None,
        )
        if winner is None:
            return chosen
        chosen.add((winner["capacity"], winner["subproblem"]))
        current = winner["subproblem"]


def text(x, y, content, size, fill=TEXT, anchor="middle", weight="normal"):
    return (
        f'<text x="{x:.1f}" y="{y:.1f}" font-family="{FONT}" font-size="{size}" '
        f'font-weight="{weight}" fill="{fill}" text-anchor="{anchor}">{content}</text>'
    )


def arc(x_from, x_to, object_type, width):
    """A curved edge from x_from to x_to, with its arrowhead drawn as a polygon."""
    colour = TYPE_COLOURS[object_type]
    side = TYPE_SIDE[object_type]
    angle = TYPE_ANGLE[object_type]
    span = x_to - x_from
    offset_x = RADIUS * math.cos(angle)
    offset_y = side * RADIUS * math.sin(angle)
    start = (x_from + offset_x, LINE_Y - offset_y)
    tip = (x_to - offset_x, LINE_Y - offset_y)
    control_y = LINE_Y - side * ARC_HEIGHT * span
    control_a = (x_from + ARC_INSET * span, control_y)
    control_b = (x_to - ARC_INSET * span, control_y)

    length = math.hypot(tip[0] - control_b[0], tip[1] - control_b[1])
    unit_x = (tip[0] - control_b[0]) / length
    unit_y = (tip[1] - control_b[1]) / length
    head_length = ARROW_LENGTH + (width - EDGE_WIDTH)
    head_width = ARROW_HALF_WIDTH + (width - EDGE_WIDTH)
    base = (tip[0] - head_length * unit_x, tip[1] - head_length * unit_y)
    corner_a = (base[0] - head_width * unit_y, base[1] + head_width * unit_x)
    corner_b = (base[0] + head_width * unit_y, base[1] - head_width * unit_x)

    points = " ".join(f"{x:.1f},{y:.1f}" for x, y in (tip, corner_a, corner_b))
    curve = " ".join(f"{x:.1f} {y:.1f}" for x, y in (control_a, control_b, base))
    return (
        f'<path d="M {start[0]:.1f} {start[1]:.1f} C {curve}" fill="none" '
        f'stroke="{colour}" stroke-width="{width}"/>'
        f'<polygon points="{points}" fill="{colour}"/>'
    )


def build_svg(vertices, edges):
    profits = {vertex["capacity"]: vertex["max_profit"] for vertex in vertices}
    root = max(profits)
    has_edge = {edge["capacity"] for edge in edges}
    chosen = optimal_edges(root, profits, edges)
    types = {edge["object_type"]: (edge["weight"], edge["profit"]) for edge in edges}
    weights = ", ".join(str(types[i][0]) for i in sorted(types))
    item_profits = ", ".join(str(types[i][1]) for i in sorted(types))
    width = 2 * MARGIN + root * UNIT

    def position(capacity):
        return MARGIN + (root - capacity) * UNIT

    parts = [
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{width * PNG_SCALE}" '
        f'height="{HEIGHT * PNG_SCALE}" viewBox="0 0 {width} {HEIGHT}">',
        f'<rect width="{width}" height="{HEIGHT}" fill="white"/>',
        text(width / 2, 34, f"Subproblem graph of P({root})", 22, weight="bold"),
        text(
            width / 2, 60,
            f"w = ({weights}), p = ({item_profits}). "
            "An edge I → J means that computing I directly calls J. "
            "Each vertex shows its capacity and its value.",
            14, MUTED,
        ),
    ]

    # Thin edges first, so the optimal choices and the vertices stay on top.
    for edge in sorted(edges, key=lambda e: (e["capacity"], e["subproblem"]) in chosen):
        is_chosen = (edge["capacity"], edge["subproblem"]) in chosen
        parts.append(arc(
            position(edge["capacity"]), position(edge["subproblem"]), edge["object_type"],
            OPTIMAL_EDGE_WIDTH if is_chosen else EDGE_WIDTH,
        ))

    for capacity, profit in profits.items():
        x = position(capacity)
        fill = "white" if capacity in has_edge else BASE_CASE_FILL
        parts.append(
            f'<circle cx="{x}" cy="{LINE_Y}" r="{RADIUS}" fill="{fill}" '
            f'stroke="{TEXT}" stroke-width="1.5"/>'
        )
        parts.append(text(x, LINE_Y - 3, f"P({capacity})", 13, weight="bold"))
        parts.append(text(x, LINE_Y + 14, str(profit), 13))

    # Legend: one row below the graph.
    x = MARGIN
    for object_type in sorted(types):
        weight, profit = types[object_type]
        parts.append(
            f'<line x1="{x}" y1="{LEGEND_Y - 5}" x2="{x + 30}" y2="{LEGEND_Y - 5}" '
            f'stroke="{TYPE_COLOURS[object_type]}" stroke-width="3"/>'
        )
        parts.append(text(
            x + 38, LEGEND_Y, f"type {object_type}: w = {weight}, p = {profit}", 14, anchor="start",
        ))
        x += 196
    parts.append(
        f'<line x1="{x}" y1="{LEGEND_Y - 5}" x2="{x + 30}" y2="{LEGEND_Y - 5}" '
        f'stroke="{MUTED}" stroke-width="{OPTIMAL_EDGE_WIDTH}"/>'
    )
    parts.append(text(x + 38, LEGEND_Y, f"optimal choices for P({root})", 14, anchor="start"))
    x += 244
    parts.append(
        f'<circle cx="{x + 10}" cy="{LEGEND_Y - 5}" r="9" fill="{BASE_CASE_FILL}" '
        f'stroke="{TEXT}" stroke-width="1.2"/>'
    )
    parts.append(text(x + 28, LEGEND_Y, "no object fits", 14, anchor="start"))
    parts.append("</svg>")
    return "\n".join(parts) + "\n"


def main():
    parser = argparse.ArgumentParser(
        description="Plot the subproblem graph written by ExperimentRunner."
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
        help="directory for the generated files (default: repository plots/)",
    )
    arguments = parser.parse_args()
    arguments.output_directory.mkdir(parents=True, exist_ok=True)

    vertices = read_rows(arguments.results_directory / "part_2_vertices.csv")
    edges = read_rows(arguments.results_directory / "part_2_edges.csv")
    svg_file = arguments.output_directory / "part_2_subproblem_graph.svg"
    svg_file.write_text(build_svg(vertices, edges), encoding="utf-8")

    if shutil.which("sips"):
        subprocess.run(
            ["sips", "-s", "format", "png", str(svg_file),
             "--out", str(svg_file.with_suffix(".png"))],
            check=True,
            stdout=subprocess.DEVNULL,
        )
    else:
        print("sips not found: wrote the SVG only.")
    print(f"Plots saved in: {arguments.output_directory.resolve()}")


if __name__ == "__main__":
    main()
