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
| 2 | `02_physique_ladder.json` | 5 body stages from the chosen design (D_3d v1_cape, seed 4001), two prompt structures: A = build words near the start, B = build words as the last sentence (causal text encoder, so everything before encodes identically). Row C = same as B with the physique sentence in Chinese. Compare drift. |
| 3 | `03_pose_locked_ladder.json` | Fallback if prompt structure isn't enough: image-to-image from the chosen hero with a DWPose skeleton locking the pose via the Z-Image Fun ControlNet. Needs controlnet_aux + the 2.9 GB model patch. |
| 4 | `04_interpolated_ladder.json` | Only the two extremes as text (last sentence, length-matched); stages in between are blends of the two encodings (ConditioningAverage). EN and ZH rows. |
| 5 | `05_even_ladder.json` | 8 stages, English only, blend values re-spaced from round 4's measured steps so each step changes about equally. |
| 6 | *(next)* | Poses (idle, flex, power-up) and layer separation. |

`art/tools/` holds the scripts that generate the workflows, so they can be rebuilt and tweaked.

## Findings so far
- **Round 1:** picked D_3d v1_cape (seed 4001). The emblem stays as is.
- **Round 2:** seeds barely matter in Z-Image, but every prompt change reshuffles the image. With the build
  sentence LAST (row B), pose, framing and face held best, but the body changed least. Chinese (row C) gave
  more range with a little more drift. All rows bunched the low end: untrained, average and athletic look
  alike.
- **Round 4:** blending two length-matched extremes works: big range, identity and costume hold. The response
  is an S-curve: little change below 20% or above 80%, most of it between 20 and 60%. English and Chinese
  behave the same. Stance widens and the belt colour wobbles in the middle stages.
