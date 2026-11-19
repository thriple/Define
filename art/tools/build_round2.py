"""Round 2: physique ladder by changing ONLY the build words (same seed and prompt as the chosen D_3d v1_cape)."""
import pathlib
from comfy_wf import Workflow, STYLE_3D, POSE, END

SEED = 4001  # seed of r1_D_3d_v1_cape
COSTUME_V1 = ("He wears a sleek form-fitting suit in hot magenta and deep navy with electric cyan accent lines "
              "along the arms and sides, navy gloves and boots, and a short sunset-orange cape that hangs behind "
              "his shoulders. A navy domino mask covers his eyes, with glowing cyan eye lenses; short dark hair. "
              "On his chest is a bold geometric emblem: ")
EMBLEM_V1 = "a white diagonal dumbbell inside a cyan diamond."

def prompt(build, emblem=EMBLEM_V1, extra=""):
    return (STYLE_3D + "\n\nFull-body character design of an original superhero, an adult man with " + build + ", "
            + POSE + COSTUME_V1 + emblem + extra + END)

LADDER = [
    ("s1_untrained", "a slim, untrained build, thin arms and a narrow chest"),
    ("s2_average", "an average build, slightly toned arms"),
    ("s3_athletic", "an athletic build"),  # identical to round 1 -> should reproduce it exactly
    ("s4_muscular", "a muscular build, strong defined arms, broad shoulders and a solid chest"),
    ("s5_prime", "a powerfully muscular heroic build, big arms, very broad shoulders and a deep chest"),
]
wf = Workflow()
wf.note("DEFINE art test, round 2\n\nPress Run once: 5 images.\n\n"
        "Physique ladder: same seed and prompt as your pick, only the body-build words change. "
        "s3_athletic is the control and should match round 1 exactly.\n\n"
        
        "Paste the results here; seeds are fixed, so screenshots or webp are fine.")
for i, (tag, build) in enumerate(LADDER):
    wf.t2i(prompt(build), SEED, f"define/r2_ladder_{tag}", (400 + i * 1150, 0), f"Ladder {tag}")
wf.group("Physique ladder (only the build words change)", (380, -50, 5750, 560))
wf.save(pathlib.Path(__file__).resolve().parents[1] / "workflows" / "02_physique_ladder.json")

# sanity: the s3 prompt must equal round 1's D_3d v1_cape prompt
import json
r1 = json.load(open(pathlib.Path(__file__).resolve().parents[1] / "workflows" / "01_style_explore.json"))
r1p = next(n for n in r1["nodes"] if n.get("title") == "Prompt D_3d v1_cape")["widgets_values"][0]
r1s = next(n for n in r1["nodes"] if n["type"] == "KSampler" and n["widgets_values"][0] == 4001)
assert prompt("an athletic build") == r1p, "control prompt differs from round 1"
print("control prompt matches round 1 exactly")
