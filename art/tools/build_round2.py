"""Round 2: physique ladder, two prompt structures side by side (same seed as the chosen D_3d v1_cape).

Row A: round 1 structure, build words near the start of the prompt.
Row B: build moved to the final sentence. Z-Image's text encoder (Qwen3) is causal: each token's encoding
depends only on the tokens before it, so with the build last, everything before it encodes identically at
every stage and only the final sentence differs."""
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
def prompt_tail(build):
    return (STYLE_3D + "\n\nFull-body character design of an original superhero, an adult man "
            + POSE + COSTUME_V1 + EMBLEM_V1 + END + "\n\nHis physique: " + build + ".")

def main():
    import json
    wf = Workflow()
    wf.note("DEFINE art test, round 2\n\nPress Run once: 10 images.\n\n"
            "Row A: build words near the start of the prompt (round 1 structure). "
            "A s3_athletic is the control and should match round 1 exactly.\n\n"
            "Row B: identical prompt text with the build as the LAST sentence, so everything before it "
            "encodes identically. Compare drift between rows.\n\n"
        
            "Paste the results here; seeds are fixed, so screenshots or webp are fine.")
    for i, (tag, build) in enumerate(LADDER):
        wf.t2i(prompt(build), SEED, f"define/r2A_{tag}", (400 + i * 1150, 0), f"A {tag}")
        wf.t2i(prompt_tail(build), SEED, f"define/r2B_{tag}", (400 + i * 1150, 620), f"B {tag}")
    wf.group("Row A: build words near the start", (380, -50, 5750, 560))
    wf.group("Row B: build words last", (380, 570, 5750, 560))
    wf.save(pathlib.Path(__file__).resolve().parents[1] / "workflows" / "02_physique_ladder.json")

    r1 = json.load(open(pathlib.Path(__file__).resolve().parents[1] / "workflows" / "01_style_explore.json"))
    r1p = next(n for n in r1["nodes"] if n.get("title") == "Prompt D_3d v1_cape")["widgets_values"][0]
    assert prompt("an athletic build") == r1p, "control prompt differs from round 1"
    print("control prompt matches round 1 exactly")


if __name__ == "__main__":
    main()
