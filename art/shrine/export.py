"""Bake Blockbench cuboids and meshes into shaded model-space quads in block units."""

import argparse
import json
import math
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
MODEL = Path(__file__).with_name("shrine.bbmodel")
OUTPUT = ROOT / "src/client/resources/assets/cursed-oath/models/domain/shrine.json"


def transform(point, element):
    px, py, pz = point
    ox, oy, oz = element.get("origin", [0, 0, 0])
    x, y, z = px - ox, py - oy, pz - oz
    rx, ry, rz = map(math.radians, element.get("rotation", [0, 0, 0]))
    y, z = y * math.cos(rx) - z * math.sin(rx), y * math.sin(rx) + z * math.cos(rx)
    x, z = x * math.cos(ry) + z * math.sin(ry), -x * math.sin(ry) + z * math.cos(ry)
    x, y = x * math.cos(rz) - y * math.sin(rz), x * math.sin(rz) + y * math.cos(rz)
    return [round(value / 16, 5) for value in (x + ox, y + oy, z + oz)]


def shaded(rgb, vertices):
    a, b, c = vertices[:3]
    u = [y - x for x, y in zip(a, b)]
    v = [y - x for x, y in zip(a, c)]
    normal = [
        u[1] * v[2] - u[2] * v[1],
        u[2] * v[0] - u[0] * v[2],
        u[0] * v[1] - u[1] * v[0],
    ]
    total = sum(abs(n) for n in normal)
    if total == 0:
        raise ValueError("Degenerate face after coordinate rounding")
    light = (
        abs(normal[0]) * 0.6
        + abs(normal[1]) * (1 if normal[1] > 0 else 0.5)
        + abs(normal[2]) * 0.8
    ) / total
    return sum(int(((rgb >> shift) & 255) * light) << shift for shift in (16, 8, 0))


def bake(model):
    """Bake visible faces without mutating the model; reject unapplied group state."""

    def check_groups(nodes):
        for node in nodes:
            if isinstance(node, dict):
                if any(node.get("rotation", [0, 0, 0])) or not node.get("visibility", True):
                    raise ValueError(f"Apply group rotation/visibility to elements: {node['name']}")
                check_groups(node.get("children", []))

    check_groups(model.get("outliner", []))
    palette = []
    for texture in model["textures"]:
        suffix = re.search(r"_([0-9a-fA-F]{6})$", texture["name"])
        if suffix is None:
            raise ValueError(f"Invalid palette name (expected name_RRGGBB): {texture['name']}")
        palette.append(int(suffix[1], 16))
    faces = []
    for element in model["elements"]:
        if element.get("visibility", True) is False:
            continue
        if element["type"] == "cube":
            lo, hi = element["from"], element["to"]
            vertices = [
                [lo[0], lo[1], lo[2]],
                [hi[0], lo[1], lo[2]],
                [hi[0], hi[1], lo[2]],
                [lo[0], hi[1], lo[2]],
                [lo[0], lo[1], hi[2]],
                [hi[0], lo[1], hi[2]],
                [hi[0], hi[1], hi[2]],
                [lo[0], hi[1], hi[2]],
            ]
            sides = {
                "north": [3, 2, 1, 0],
                "south": [6, 7, 4, 5],
                "west": [7, 3, 0, 4],
                "east": [2, 6, 5, 1],
                "up": [7, 6, 2, 3],
                "down": [0, 1, 5, 4],
            }
            polygons = [
                (indices, element["faces"][side]["texture"])
                for side, indices in sides.items()
            ]
        elif element["type"] == "mesh":
            vertices = element["vertices"]
            polygons = [
                (face["vertices"], face["texture"])
                for face in element["faces"].values()
            ]
        else:
            raise ValueError(f"Unsupported element: {element['type']}")
        items = enumerate(vertices) if isinstance(vertices, list) else vertices.items()
        vertices = {key: transform(point, element) for key, point in items}
        if not all(math.isfinite(value) for point in vertices.values() for value in point):
            raise ValueError(f"Non-finite coordinates in {element.get('name', element['type'])}")
        for indices, material in polygons:
            if material is None:
                continue
            points = [vertices[index] for index in indices]
            if len(points) == 3:
                points.append(points[-1])
            if len(points) != 4:
                raise ValueError(
                    "Triangulate polygons with more than four vertices before export"
                )
            if type(material) is not int or not 0 <= material < len(palette):
                raise ValueError(f"Unknown material index: {material}")
            try:
                color = shaded(palette[material], points)
            except ValueError as error:
                raise ValueError(f"{element.get('name', element['type'])}: {error}") from error
            faces.append({"vertices": points, "color": color})
    return {"faces": faces}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="fail if the baked resource is stale")
    args = parser.parse_args()
    model = json.loads(MODEL.read_text(encoding="utf-8"))
    result = bake(model)
    text = json.dumps(result, separators=(",", ":"), allow_nan=False) + "\n"
    if args.check:
        if not OUTPUT.exists() or OUTPUT.read_text(encoding="utf-8") != text:
            parser.exit(1, "Shrine resource is stale; run python3 art/shrine/export.py\n")
        print(f"Verified {len(result['faces'])} faces")
    else:
        OUTPUT.parent.mkdir(parents=True, exist_ok=True)
        OUTPUT.write_text(text, encoding="utf-8")
        print(f"Exported {len(result['faces'])} faces")


if __name__ == "__main__":
    main()
