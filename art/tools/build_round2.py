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
    ("s1_untrained", "a skinny, out-of-shape build: thin noodle arms, narrow sloping shoulders, a flat chest and a slightly soft belly"),
    ("s2_average", "a lean build with lightly toned arms and slightly broader shoulders"),
    ("s3_athletic", "an athletic build"),  # identical to round 1 -> should reproduce it exactly
    ("s4_muscular", "a muscular build, strong defined arms, broad shoulders and a solid chest"),
    ("s5_prime", "a powerfully muscular heroic build, big arms, very broad shoulders and a deep chest"),
]
def prompt_tail(build):
    return (STYLE_3D + "\n\nFull-body character design of an original superhero, an adult man "
            + POSE + COSTUME_V1 + EMBLEM_V1 + END + "\n\nHis physique: " + build + ".")

# Same stages, physique sentence in Chinese (Z-Image was trained bilingually; its Qwen3 encoder is
# natively strong in Chinese, and English body-type vocabulary can have gaps).
LADDER_ZH = {
    "s1_untrained": "他的体型：瘦弱又缺乏锻炼，手臂细得像面条，肩膀窄而下垂，胸部扁平，肚子有点软。",
    "s2_average": "他的体型：偏瘦但略有锻炼，手臂有一点线条，肩膀稍微宽一些。",
    "s3_athletic": "他的体型：匀称健美的运动员身材。",
    "s4_muscular": "他的体型：肌肉发达，手臂粗壮结实，肩膀宽阔，胸肌厚实。",
    "s5_prime": "他的体型：极其强壮的超级英雄身材，手臂肌肉巨大，肩膀非常宽阔，胸肌非常厚实饱满。",
}


def prompt_tail_zh(tag):
    return (STYLE_3D + "\n\nFull-body character design of an original superhero, an adult man "
            + POSE + COSTUME_V1 + EMBLEM_V1 + END + "\n\n" + LADDER_ZH[tag])


def main():
    import json
    wf = Workflow()
    wf.note("DEFINE art test, round 2\n\nPress Run once: 15 images.\n\n"
            "Row A: build words near the start of the prompt (round 1 structure). "
            "A s3_athletic is the control and should match round 1 exactly.\n\n"
            "Row B: identical prompt text with the build as the LAST sentence, so everything before it "
            "encodes identically.\n\n"
            "Row C: same as B, but the physique sentence is in Chinese.\n\n"
            "Compare drift (pose, framing, costume, face) and how much the body actually changes.\n\n"
        
            "Paste the results here; seeds are fixed, so screenshots or webp are fine.")
    for i, (tag, build) in enumerate(LADDER):
        wf.t2i(prompt(build), SEED, f"define/r2A_{tag}", (400 + i * 1150, 0), f"A {tag}")
        wf.t2i(prompt_tail(build), SEED, f"define/r2B_{tag}", (400 + i * 1150, 620), f"B {tag}")
        wf.t2i(prompt_tail_zh(tag), SEED, f"define/r2C_{tag}", (400 + i * 1150, 1240), f"C {tag}")
    wf.group("Row A: build words near the start", (380, -50, 5750, 560))
    wf.group("Row B: build words last", (380, 570, 5750, 560))
    wf.group("Row C: build words last, in Chinese", (380, 1190, 5750, 560))
    wf.save(pathlib.Path(__file__).resolve().parents[1] / "workflows" / "02_physique_ladder.json")

    r1 = json.load(open(pathlib.Path(__file__).resolve().parents[1] / "workflows" / "01_style_explore.json"))
    r1p = next(n for n in r1["nodes"] if n.get("title") == "Prompt D_3d v1_cape")["widgets_values"][0]
    assert prompt("an athletic build") == r1p, "control prompt differs from round 1"
    print("control prompt matches round 1 exactly")


if __name__ == "__main__":
    main()
