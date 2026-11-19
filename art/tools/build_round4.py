"""Round 4: interpolated physique ladder.

Only the two extremes are written as text (scrawny / hugely muscular), as the LAST sentence of an otherwise
identical prompt (row B structure from round 2, which drifted least). In-between stages are a weighted blend
of the two encodings (core ConditioningAverage), so the ladder is smooth and can have any number of stages.
Endpoint sentences are length-matched to within one token (Qwen tokenizer) so the blend lines up.
One row in English, one with the physique sentence in Chinese."""
import pathlib
from comfy_wf import Workflow, STYLE_3D, POSE, END
from build_round2 import COSTUME_V1, EMBLEM_V1, SEED

PREFIX = (STYLE_3D + "\n\nFull-body character design of an original superhero, an adult man "
          + POSE + COSTUME_V1 + EMBLEM_V1 + END + "\n\n")
ENDPOINTS = {
    "EN": ("His physique: very scrawny and out of shape, skinny noodle arms with no muscle, narrow sloping "
           "shoulders, a sunken flat chest and a small soft belly.",
           "His physique: a hugely muscular superhero build, massive bulging arms, very broad and powerful "
           "shoulders, a huge deep chest, a narrow tapered waist and thick, strong legs."),
    "ZH": ("他的体型：骨瘦如柴，完全没有肌肉，手臂细得像面条，肩膀窄而下垂，胸部凹陷扁平，小腹微微隆起。",
           "他的体型：肌肉极其发达的超级英雄身材，手臂肌肉巨大隆起，肩膀非常宽阔，胸肌巨大厚实，腰部收窄，双腿粗壮。"),
}
MIX = [0.0, 0.2, 0.4, 0.6, 0.8, 1.0]  # share of the "hugely muscular" endpoint


# Round 5: round 4 showed an S-curve (little change below 20% or above 80%, most of it between 20 and 60%)
# and no real difference between English and Chinese. These blend values are spaced so each step changes
# the image by about the same amount (inverted from the measured round 4 step sizes, EN row).
MIX_EVEN = [0.0, 0.23, 0.33, 0.42, 0.50, 0.58, 0.73, 1.0]


def main(langs=("EN", "ZH"), mix=MIX, out="04_interpolated_ladder.json", pre="r4", label="round 4: interpolated physique ladder"):
    wf = Workflow()
    wf.note(f"DEFINE art test, {label}\n\n"
            f"Press Run once: {len(langs) * len(mix)} images.\n\n"
            "Only the two extremes are written as text. Stages in between blend the two encodings: "
            "m00 = fully scrawny, m100 = fully muscular.\n\n"
            "Rows: physique sentence in English (EN) / Chinese (ZH).\n\n"
            "Check: even steps between stages? Pose/costume/face steady?")
    for row, lang in enumerate(langs):
        low, high = ENDPOINTS[lang]
        y = row * 700
        lo = wf.encode(PREFIX + low, (400, y), f"{lang} low (scrawny)")
        hi = wf.encode(PREFIX + high, (400, y + 300), f"{lang} high (muscular)")
        for i, m in enumerate(mix):
            x = 900 + i * 700
            avg = wf.node("ConditioningAverage", (x, y - 120), (300, 80), [m],
                          inputs=[("conditioning_to", "CONDITIONING"), ("conditioning_from", "CONDITIONING")],
                          outputs=[("CONDITIONING", "CONDITIONING")], title=f"{lang} mix {int(m * 100)}% muscular")
            wf.link(hi, 0, avg, "conditioning_to"); wf.link(lo, 0, avg, "conditioning_from")
            name = f"{pre}_{lang}_m{round(m * 100):03d}" if pre == "r4" else f"{pre}_stage{i + 1}_m{round(m * 100):03d}"
            wf.sample(avg, SEED, f"define/{name}", (x, y), f"{lang} m{round(m * 100)}")
        wf.group(f"Row {lang}", (380, y - 170, 900 + len(mix) * 700 - 380, 680))
    wf.save(pathlib.Path(__file__).resolve().parents[1] / "workflows" / out)


if __name__ == "__main__":
    main()
    main(langs=("EN",), mix=MIX_EVEN, out="05_even_ladder.json", pre="r5", label="round 5: evenly spaced 8-stage ladder")
