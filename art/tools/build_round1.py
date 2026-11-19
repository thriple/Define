"""Builds art/workflows/01_style_explore.json (ComfyUI UI-format workflow).

A grid of the same original hero: four art styles (rows) x three costumes
(columns), one image each, all in a single run, with Z-Image Turbo. Settings mirror Comfy's official
Z-Image Turbo template (8 steps, cfg 1, res_multistep/simple, AuraFlow shift 3).
"""
import json, pathlib

BASE = (
    "Full-body character design of an original superhero, an adult man with an athletic build, "
    "standing facing the viewer in a confident heroic stance, feet shoulder-width apart, arms relaxed "
    "and held slightly away from his body, the whole figure visible from head to toe with empty space "
    "around it. "
)
END = " Plain flat pale grey background, no scenery, no text, no logos, no watermark."

# Z-Image Turbo barely varies between seeds, so variety comes from the prompt, not the seed.
COSTUMES = [
    ("v1_cape",
     "He wears a sleek form-fitting suit in hot magenta and deep navy with electric cyan accent lines "
     "along the arms and sides, navy gloves and boots, and a short sunset-orange cape that hangs behind "
     "his shoulders. A navy domino mask covers his eyes, with glowing cyan eye lenses; short dark hair. "
     "On his chest is a bold geometric emblem: a white diagonal dumbbell inside a cyan diamond."),
    ("v2_cowl",
     "He wears a deep navy suit with hot magenta armour panels on the chest, shoulders and shins, thin "
     "glowing cyan seams, and no cape. A full navy cowl covers his head, with a single wide glowing cyan "
     "visor across the eyes. On his chest is a bold geometric emblem: a white diagonal dumbbell inside an "
     "orange circle."),
    ("v3_sleeveless",
     "He wears a sleeveless hot magenta and sunset-orange suit that leaves his arms bare, chunky navy "
     "gauntlets on his forearms, a navy belt, navy boots, and no cape. A navy half-mask covers the upper "
     "face, with glowing cyan eye lenses; short dark hair. On his chest is a bold geometric emblem: a "
     "white diagonal dumbbell inside a navy diamond with a cyan outline."),
]

STYLES = [
    ("A_comic", 1001,
     "Modern American comic book illustration, bold confident black ink linework, dynamic cel shading "
     "with crisp graphic shadow shapes, vibrant saturated colors, professional comic cover quality."),
    ("B_popart", 2001,
     "1960s pop-art comic style, flat bold colors, thick black outlines, visible Ben-Day halftone dots "
     "in the shading, printed comic book texture."),
    ("C_animated", 3001,
     "Clean modern animated-series character design, smooth simple shapes, thick uniform outlines, flat "
     "colors with one soft shadow tone, appealing simplified anatomy, character model sheet style."),
    ("D_3d", 4001,
     "Stylized 3D animated feature film character render, soft studio lighting, slightly glossy suit "
     "material, appealing stylized proportions, high-end animation studio quality."),
]

W, H, BATCH = 832, 1216, 1

nodes, links = [], []
_link = [0]

def node(nid, ntype, pos, size, widgets=None, inputs=(), outputs=(), title=None):
    n = {
        "id": nid, "type": ntype, "pos": list(pos), "size": list(size), "flags": {},
        "order": len(nodes), "mode": 0,
        "inputs": [{"name": nm, "type": tp, "link": None} for nm, tp in inputs],
        "outputs": [{"name": nm, "type": tp, "links": [], "slot_index": i} for i, (nm, tp) in enumerate(outputs)],
        "properties": {"Node name for S&R": ntype},
        "widgets_values": widgets if widgets is not None else [],
    }
    if title:
        n["title"] = title
    nodes.append(n)
    return n

def link(src, sslot, dst, dname):
    _link[0] += 1
    lid = _link[0]
    tp = src["outputs"][sslot]["type"]
    src["outputs"][sslot]["links"].append(lid)
    inp = next(i for i in dst["inputs"] if i["name"] == dname)
    assert inp["type"] == tp, (dname, inp["type"], tp)
    inp["link"] = lid
    links.append([lid, src["id"], sslot, dst["id"], [i["name"] for i in dst["inputs"]].index(dname), tp])

note = node(1, "Note", (-420, 0), (380, 420), [
    "DEFINE art test, round 1: style exploration\n\n"
    "1. If a model box below is red, click it and pick your Z-Image Turbo files "
    "(diffusion model, qwen_3_4b text encoder, ae VAE).\n"
    "2. Press Run once. It makes 12 images: 4 styles x 3 costumes.\n"
    "3. Outputs land in ComfyUI/output/define/.\n"
    "4. Upload the PNGs (originals, not screenshots: they carry the seeds) to "
    "art/inbox/round1 on the GitHub branch, or paste your favourites into chat."
])
unet = node(2, "UNETLoader", (0, 0), (320, 82), ["z_image_turbo_bf16.safetensors", "default"], outputs=[("MODEL", "MODEL")])
clip = node(3, "CLIPLoader", (0, 120), (320, 106), ["qwen_3_4b.safetensors", "lumina2", "default"], outputs=[("CLIP", "CLIP")])
vae = node(4, "VAELoader", (0, 270), (320, 58), ["ae.safetensors"], outputs=[("VAE", "VAE")])
msaf = node(5, "ModelSamplingAuraFlow", (0, 370), (320, 58), [3], inputs=[("model", "MODEL")], outputs=[("MODEL", "MODEL")])
latent = node(6, "EmptySD3LatentImage", (0, 470), (320, 106), [W, H, BATCH], outputs=[("LATENT", "LATENT")])
link(unet, 0, msaf, "model")

groups = []
nid = 10
for row, (tag, seed, style) in enumerate(STYLES):
    y = row * 560
    for col, (ctag, costume) in enumerate(COSTUMES):
        x0 = 400 + col * 1150
        te = node(nid, "CLIPTextEncode", (x0, y), (420, 300), [style + "\n\n" + BASE + costume + END],
                  inputs=[("clip", "CLIP")], outputs=[("CONDITIONING", "CONDITIONING")], title=f"Prompt {tag} {ctag}")
        zo = node(nid + 1, "ConditioningZeroOut", (x0, y + 340), (240, 26),
                  inputs=[("conditioning", "CONDITIONING")], outputs=[("CONDITIONING", "CONDITIONING")])
        ks = node(nid + 2, "KSampler", (x0 + 440, y), (300, 262), [seed + col, "fixed", 8, 1, "res_multistep", "simple", 1],
                  inputs=[("model", "MODEL"), ("positive", "CONDITIONING"), ("negative", "CONDITIONING"), ("latent_image", "LATENT")],
                  outputs=[("LATENT", "LATENT")])
        de = node(nid + 3, "VAEDecode", (x0 + 440, y + 300), (200, 46),
                  inputs=[("samples", "LATENT"), ("vae", "VAE")], outputs=[("IMAGE", "IMAGE")])
        sv = node(nid + 4, "SaveImage", (x0 + 760, y), (340, 500), [f"define/r1_{tag}_{ctag}"], inputs=[("images", "IMAGE")])
        link(clip, 0, te, "clip"); link(te, 0, zo, "conditioning")
        link(msaf, 0, ks, "model"); link(te, 0, ks, "positive"); link(zo, 0, ks, "negative"); link(latent, 0, ks, "latent_image")
        link(ks, 0, de, "samples"); link(vae, 0, de, "vae"); link(de, 0, sv, "images")
        nid += 10
    groups.append({"title": f"Style {tag}", "bounding": [380, y - 50, 3480, 560], "color": "#3f789e", "font_size": 24})

wf = {
    "last_node_id": max(n["id"] for n in nodes), "last_link_id": _link[0],
    "nodes": nodes, "links": links, "groups": groups,
    "config": {}, "extra": {"ds": {"scale": 0.6, "offset": [500, 100]}}, "version": 0.4,
}
out = pathlib.Path(__file__).resolve().parents[1] / "workflows" / "01_style_explore.json"
out.write_text(json.dumps(wf, indent=1))
print("wrote", out, len(nodes), "nodes", len(links), "links")
