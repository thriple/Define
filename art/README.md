# Define: art pipeline (manual handoff)

Claude writes ComfyUI workflows into `art/workflows/`. You run them on your PC and upload the results
to `art/inbox/<round>/`. Claude reviews them, cleans them up and builds the motion.

## Each round
1. Open the workflow file on GitHub (branch `claude/bold-feynman-9swjqd`), click **Download raw file**,
   and drag the `.json` onto the ComfyUI canvas.
2. If a model box is red, click it and pick your file.
3. Press **Run** as many times as the note in the workflow says.
4. Outputs land in `ComfyUI/output/define/`.
5. On GitHub, go to `art/inbox/<round>/` on the same branch → **Add file → Upload files**, drop the
   PNGs in, and commit directly to the branch. Upload the original PNGs, not screenshots: ComfyUI
   stores the seed and settings inside each PNG, so any image can be reproduced exactly.

## Rounds
| # | Workflow | Goal |
|---|---|---|
| 1 | `01_style_explore.json` | Grid: 4 styles (comic, pop art, animated series, 3D) × 3 costumes (cape, cowl, sleeveless). Pick a style and a costume direction. |
| 2 | `02_physique_ladder.json` | Chosen design (D_3d v1_cape, seed 4001): 5 body stages by changing only the build words. Tests whether the hero stays consistent without pose control. |
| 3 | *(next)* | Physique ladder: same pose locked with the Z-Image ControlNet, from day one to Prime. |
| 4 | *(next)* | Poses (idle, flex, power-up) and layer separation. |

`art/tools/` holds the scripts that generate the workflows, so they can be rebuilt and tweaked.
