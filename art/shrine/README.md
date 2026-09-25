# Shrine model

[shrine.bbmodel](shrine.bbmodel) is the editable Blockbench generic model. Sixteen model units equal one Minecraft block; Y points up. The model is about 16.6 blocks tall and 18.2 blocks wide across its outermost bones, with a 16.6-block roof span. At runtime it appears eleven blocks behind the domain origin, aligned with the casting direction.

## Shape and materials

Four projecting mouths have separate upper jaws, recessed palates, lower jaws, and beveled teeth. The front and rear roof gables each contain a smaller mouth. Keep the roof horns, ridge-end ox masks, tile seams, and layered eaves legible in silhouette. Recessed eye and nasal cavities distinguish the skulls around the low bone mound.

The broad roof has a continuous timber underside and closed fascia. A bearing frieze follows the underside above the lintels; stepped brackets join the pillar capitals to this frame. Preserve these connections when changing the roof span or elevation, and inspect them from below.

Textures are embedded 16×16 solid palettes. Their names must end in `_RRGGBB`: the exporter reads that color and does not sample image pixels. UVs remain available for Blockbench editing, but painting textures alone does not change the runtime model.

## Export and verify

Run from the repository root:

```sh
python3 art/shrine/export.py
python3 -m unittest discover -s art/shrine
python3 art/shrine/export.py --check
```

The exporter bakes element transforms and directional shading into `src/main/resources/assets/cursed-oath/models/domain/shrine.json`. Resource reload rebuilds the runtime model from this file.

Use triangles or quads; triangulate larger polygons. Apply group rotation and visibility to elements before export. Invalid materials, non-finite coordinates, degenerate faces, and unapplied group state are rejected.

The tools use only Python's standard library. Tests cover transforms, winding, lighting, triangles, hidden faces, and invalid input; `--check` compares the export with the source without rewriting it. CI runs both checks. After shape changes, inspect front, rear, side, and low oblique game captures, including both gable mouths and the roof supports.

## References

The model was constructed within the project using supplied manga and community-model screenshots as visual references; no external model geometry was imported. Reference panels and animation frames are not distributed as game assets. Canon and adaptation evidence are tracked in [sources](../../docs/sources.zh-CN.md#领域图像参考与改编).
