"""Regression tests for Blockbench transforms, face shading, and input validation."""

import copy
import unittest

from export import bake, transform


def model():
    return {
        "textures": [{"name": "bone_ffffff"}],
        "elements": [
            {
                "name": "test face",
                "type": "mesh",
                "vertices": {"a": [0, 0, 0], "b": [16, 0, 0], "c": [16, 16, 0], "d": [0, 16, 0]},
                "faces": {"front": {"vertices": ["a", "b", "c", "d"], "texture": 0}},
            }
        ],
    }


class ShrineExportTest(unittest.TestCase):
    def test_mesh_units_winding_and_input_are_preserved(self):
        source = model()
        original = copy.deepcopy(source)
        face = {
            "vertices": [[0, 0, 0], [1, 0, 0], [1, 1, 0], [0, 1, 0]],
            "color": 0xCCCCCC,
        }
        self.assertEqual(bake(source), {"faces": [face]})
        self.assertEqual(source, original)

    def test_rotation_uses_the_element_pivot(self):
        self.assertEqual(transform([32, 0, 0], {"origin": [16, 0, 0], "rotation": [0, 90, 0]}), [1, 0, -1])
        self.assertEqual(transform([0, 16, 0], {"rotation": [90, 0, 0]}), [0, 0, 1])
        self.assertEqual(transform([16, 0, 0], {"rotation": [0, 0, 90]}), [0, 1, 0])

    def test_triangle_pads_only_the_last_vertex(self):
        source = model()
        source["elements"][0]["faces"]["front"]["vertices"].pop()
        points = bake(source)["faces"][0]["vertices"]
        self.assertEqual(points, [[0, 0, 0], [1, 0, 0], [1, 1, 0], [1, 1, 0]])

    def test_hidden_elements_and_untextured_faces_are_omitted(self):
        for hidden in (True, False):
            with self.subTest(hidden=hidden):
                source = model()
                if hidden:
                    source["elements"][0]["visibility"] = False
                else:
                    source["elements"][0]["faces"]["front"]["texture"] = None
                self.assertEqual(bake(source), {"faces": []})

    def test_cube_shading_matches_vanilla_axis_lighting(self):
        source = model()
        source["elements"] = [
            {
                "type": "cube",
                "from": [0, 0, 0],
                "to": [16, 16, 16],
                "faces": {side: {"texture": 0} for side in ("north", "south", "west", "east", "up", "down")},
            }
        ]
        faces = bake(source)["faces"]
        self.assertEqual(len(faces), 6)
        for axis, low, high in ((0, 0x999999, 0x999999), (1, 0x7F7F7F, 0xFFFFFF), (2, 0xCCCCCC, 0xCCCCCC)):
            for coordinate, expected in ((0, low), (1, high)):
                with self.subTest(axis=axis, coordinate=coordinate):
                    matches = [face for face in faces if all(vertex[axis] == coordinate for vertex in face["vertices"])]
                    self.assertEqual(len(matches), 1)
                    self.assertEqual(matches[0]["color"], expected)
                    a, b, c = matches[0]["vertices"][:3]
                    u = [b[i] - a[i] for i in range(3)]
                    v = [c[i] - a[i] for i in range(3)]
                    normal = u[(axis + 1) % 3] * v[(axis + 2) % 3] - u[(axis + 2) % 3] * v[(axis + 1) % 3]
                    self.assertGreater(normal * (2 * coordinate - 1), 0, "Cube faces must point outward")

    def test_invalid_geometry_fails_with_a_useful_reason(self):
        cases = [
            ("Degenerate", lambda e: e["vertices"].update(c=[16, 0, 0])),
            ("Non-finite", lambda e: e["vertices"].update(c=[float("nan"), 0, 0])),
            ("Unknown material", lambda e: e["faces"]["front"].update(texture=-1)),
            ("material", lambda e: e["faces"]["front"].update(texture=0.5)),
            ("Triangulate", lambda e: e["faces"]["front"]["vertices"].append("a")),
            ("Unsupported", lambda e: e.update(type="locator")),
        ]
        for message, modify in cases:
            with self.subTest(message=message):
                source = model()
                modify(source["elements"][0])
                with self.assertRaisesRegex(ValueError, message):
                    bake(source)

    def test_palette_names_require_exactly_six_hex_digits(self):
        for name in ("bone_fff", "bone_1000000", "bone_-000001", "ffffff", "bone_gggggg"):
            with self.subTest(name=name):
                source = model()
                source["textures"][0]["name"] = name
                with self.assertRaisesRegex(ValueError, "palette"):
                    bake(source)

    def test_coordinate_vectors_cannot_be_silently_truncated(self):
        with self.assertRaises(ValueError):
            transform([16, 0, 0, 99], {})
        with self.assertRaises(ValueError):
            transform([16, 0, 0], {"origin": [0, 0, 0, 99]})

    def test_unapplied_group_transforms_cannot_silently_change_the_model(self):
        for properties in ({"rotation": [0, 90, 0]}, {"visibility": False}):
            with self.subTest(properties=properties):
                source = model()
                source["outliner"] = [{"name": "parent", "children": [{"name": "child", **properties}]}]
                with self.assertRaisesRegex(ValueError, "Apply group.*child"):
                    bake(source)


if __name__ == "__main__":
    unittest.main()
