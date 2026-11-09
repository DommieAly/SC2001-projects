#!/usr/bin/env python3
"""Draw the subproblem graph of part (2) from the CSV results.

Each vertex P(15 - 4a - 5b) goes in column a and row b. A type-0 edge is then
one step right, a type-1 edge one step down, and a type-2 edge two steps right,
because w2 = 2 * w0. The script needs only the standard library: it writes SVG,
and on macOS `sips` converts that to PNG.
"""

import argparse
import csv
import math
import shutil
import subprocess
from pathlib import Path

# The same categorical order as the figures of the other projects.
TYPE_COLOURS = ["#2a78d6", "#eb6834", "#1baf7a"]
# Columns and rows moved by an edge of each object type.
TYPE_STEPS = [(1, 0), (0, 1), (2, 0)]

TEXT = "#1a1a1a"
MUTED = "#5c5c5c"
BASE_CASE_FILL = "#e6e6e6"
FONT = "Helvetica, Arial, sans-serif"
BOLD_FONT = "Helvetica-Bold, Helvetica, Arial, sans-serif"

WIDTH = 780
HEIGHT = 600
LEFT = 60
TOP = 90
CELL_WIDTH = 220
CELL_HEIGHT = 150
RADIUS = 34
ARC_ANGLE = math.radians(58)
ARC_CONTROL_HEIGHT = 78
ARC_INSET = 0.2
ARROW_LENGTH = 13
ARROW_HALF_WIDTH = 6
EDGE_WIDTH = 2
OPTIMAL_EDGE_WIDTH = 4.5
LEGEND_X = 440
PNG_SCALE = 3


def read_rows(path):
    with path.open(newline="", encoding="utf-8") as file:
        return [{key: int(value) for key, value in row.items()} for row in csv.DictReader(file)]


def grid_points(root, edges):
    """The drawing position of every vertex. Edges are listed largest capacity first."""
    cells = {root: (0, 0)}
    for edge in edges:
        column, row = cells[edge["capacity"]]
        step_column, step_row = TYPE_STEPS[edge["object_type"]]
        cells[edge["subproblem"]] = (column + step_column, row + step_row)
    return {
        capacity: (LEFT + column * CELL_WIDTH, TOP + row * CELL_HEIGHT)
        for capacity, (column, row) in cells.items()
    }


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


def text(x, y, content, size, fill=TEXT, anchor="start", font=FONT, weight="normal"):
    return (
        f'<text x="{x:.1f}" y="{y:.1f}" font-family="{font}" font-size="{size}" '
        f'font-weight="{weight}" fill="{fill}" text-anchor="{anchor}">{content}</text>'
    )


def arrowhead(tip, previous, colour, width):
    """The polygon of an arrowhead at tip, and the point where its line should end."""
    length = math.hypot(tip[0] - previous[0], tip[1] - previous[1])
    unit_x = (tip[0] - previous[0]) / length
    unit_y = (tip[1] - previous[1]) / length
    head_length = ARROW_LENGTH + (width - EDGE_WIDTH)
    head_width = ARROW_HALF_WIDTH + (width - EDGE_WIDTH)
    base = (tip[0] - head_length * unit_x, tip[1] - head_length * unit_y)
    corner_a = (base[0] - head_width * unit_y, base[1] + head_width * unit_x)
    corner_b = (base[0] + head_width * unit_y, base[1] - head_width * unit_x)
    points = " ".join(f"{x:.1f},{y:.1f}" for x, y in (tip, corner_a, corner_b))
    return base, f'<polygon points="{points}" fill="{colour}"/>'


def line_with_arrow(start, tip, colour, width):
    base, head = arrowhead(tip, start, colour, width)
    return (
        f'<line x1="{start[0]:.1f}" y1="{start[1]:.1f}" x2="{base[0]:.1f}" y2="{base[1]:.1f}" '
        f'stroke="{colour}" stroke-width="{width}"/>{head}'
    )


def straight_edge(point_from, point_to, colour, width):
    length = math.hypot(point_to[0] - point_from[0], point_to[1] - point_from[1])
    unit_x = (point_to[0] - point_from[0]) / length
    unit_y = (point_to[1] - point_from[1]) / length
    start = (point_from[0] + RADIUS * unit_x, point_from[1] + RADIUS * unit_y)
    tip = (point_to[0] - RADIUS * unit_x, point_to[1] - RADIUS * unit_y)
    return line_with_arrow(start, tip, colour, width)


def arc_edge(point_from, point_to, colour, width):
    """An edge that curves over the vertex between its two ends."""
    span = point_to[0] - point_from[0]
    offset_x = RADIUS * math.cos(ARC_ANGLE)
    offset_y = RADIUS * math.sin(ARC_ANGLE)
    start = (point_from[0] + offset_x, point_from[1] - offset_y)
    tip = (point_to[0] - offset_x, point_to[1] - offset_y)
    control_y = point_from[1] - ARC_CONTROL_HEIGHT
    control_a = (point_from[0] + ARC_INSET * span, control_y)
    control_b = (point_to[0] - ARC_INSET * span, control_y)
    base, head = arrowhead(tip, control_b, colour, width)
    curve = " ".join(f"{x:.1f} {y:.1f}" for x, y in (control_a, control_b, base))
    return (
        f'<path d="M {start[0]:.1f} {start[1]:.1f} C {curve}" fill="none" '
        f'stroke="{colour}" stroke-width="{width}"/>{head}'
    )


def legend(root, types):
    parts = [text(LEGEND_X, 326, f"Subproblem graph of P({root})", 21, font=BOLD_FONT, weight="bold")]
    y = 362
    for object_type in sorted(types):
        weight, profit = types[object_type]
        parts.append(line_with_arrow(
            (LEGEND_X, y - 6), (LEGEND_X + 32, y - 6), TYPE_COLOURS[object_type], EDGE_WIDTH + 1,
        ))
        parts.append(text(LEGEND_X + 44, y, f"type {object_type}: w = {weight}, p = {profit}", 18))
        y += 31
    parts.append(line_with_arrow(
        (LEGEND_X, y - 6), (LEGEND_X + 32, y - 6), MUTED, OPTIMAL_EDGE_WIDTH,
    ))
    parts.append(text(LEGEND_X + 44, y, f"optimal choices for P({root})", 18))
    y += 31
    parts.append(
        f'<circle cx="{LEGEND_X + 13}" cy="{y - 6}" r="10" fill="{BASE_CASE_FILL}" '
        f'stroke="{TEXT}" stroke-width="1.2"/>'
    )
    parts.append(text(LEGEND_X + 44, y, "no object fits", 18))
    y += 38
    parts.append(text(LEGEND_X, y, "Each vertex shows P(C) and its value.", 15, MUTED))
    parts.append(text(LEGEND_X, y + 21, "I → J means computing I directly calls J.", 15, MUTED))
    return parts


def build_svg(vertices, edges):
    profits = {vertex["capacity"]: vertex["max_profit"] for vertex in vertices}
    root = max(profits)
    has_edge = {edge["capacity"] for edge in edges}
    chosen = optimal_edges(root, profits, edges)
    types = {edge["object_type"]: (edge["weight"], edge["profit"]) for edge in edges}
    points = grid_points(root, edges)

    parts = [
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{WIDTH * PNG_SCALE}" '
        f'height="{HEIGHT * PNG_SCALE}" viewBox="0 0 {WIDTH} {HEIGHT}">',
        f'<rect width="{WIDTH}" height="{HEIGHT}" fill="white"/>',
    ]
    # Thin edges first, so the optimal choices and the vertices stay on top.
    for edge in sorted(edges, key=lambda e: (e["capacity"], e["subproblem"]) in chosen):
        width = OPTIMAL_EDGE_WIDTH if (edge["capacity"], edge["subproblem"]) in chosen else EDGE_WIDTH
        draw = arc_edge if TYPE_STEPS[edge["object_type"]][0] == 2 else straight_edge
        parts.append(draw(
            points[edge["capacity"]], points[edge["subproblem"]],
            TYPE_COLOURS[edge["object_type"]], width,
        ))

    for capacity, profit in profits.items():
        x, y = points[capacity]
        fill = "white" if capacity in has_edge else BASE_CASE_FILL
        parts.append(
            f'<circle cx="{x}" cy="{y}" r="{RADIUS}" fill="{fill}" '
            f'stroke="{TEXT}" stroke-width="1.6"/>'
        )
        parts.append(text(x, y - 2, f"P({capacity})", 18, anchor="middle", font=BOLD_FONT, weight="bold"))
        parts.append(text(x, y + 19, str(profit), 18, MUTED, anchor="middle"))

    parts.extend(legend(root, types))
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
