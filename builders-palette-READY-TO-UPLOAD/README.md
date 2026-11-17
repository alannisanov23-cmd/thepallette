# Builder's Palette (Fabric, Minecraft 26.3)

Look at a block and a panel next to the crosshair shows blocks that match its colour. While sneaking with a block
in hand (and not looking at a block) it shows the palette for the held block.

- **Dyed blocks** (wool, concrete, terracotta, glazed terracotta, stained glass, carpet, shulker boxes, beds,
  candles, banners): the other dyed types in the same colour, plus natural look-alikes (e.g. look at Red Concrete
  and it suggests Bricks, Red Nether Bricks, Red Sandstone, Netherrack...).
- **Natural blocks**: similar blocks in the same colour family, plus the dyed blocks that match.
- Stairs, slabs, walls, fences, doors, trapdoors, buttons and pressure plates use their base block's family.

It's a hand-made colour table (17 families), so it won't know modded blocks, and a few blocks sit in the nearest family
rather than a perfect one. Text only; no mixins, no rendering changes. Standalone.

## Build
Upload this folder's contents to a GitHub repo (its workflow is `.github/workflows/builders-palette.yml`), open
Actions, download `builders-palette-jar`, use the jar WITHOUT -sources.

## Tweak
Add or move blocks in the `family(...)` lines at the top of `BuildersPaletteClient.java`.
