"""Round 3: physique ladder by image-to-image from the chosen hero, with the pose locked by a DWPose
skeleton through the Z-Image-Turbo Fun ControlNet Union. A skeleton fixes joint positions but not body
volume, so the build can change while stance and framing stay put. Two denoise strengths per stage
calibrate how far the image may move: low keeps more of the original, high allows more muscle change."""
import pathlib
from comfy_wf import Workflow
from build_round2 import prompt, LADDER, SEED

SOURCE = "r1_D_3d_v1_cape_00001_.png"
DENOISES = [0.55, 0.75]
STAGES = [l for l in LADDER if l[0] != "s3_athletic"]

wf = Workflow(link_model=False)
wf.note("DEFINE art test, round 3: pose-locked physique ladder\n\n"
        "One-time setup:\n"
        "a) Manager > Custom Nodes > install \"ComfyUI's ControlNet Auxiliary Preprocessors\", restart.\n"
        "b) Put Z-Image-Turbo-Fun-Controlnet-Union.safetensors in models/model_patches.\n"
        "c) Update ComfyUI if the ModelPatchLoader node shows red.\n\n"
        "Each run:\n"
        "1. In Load Image, upload your round 1 pick (output/define/r1_D_3d_v1_cape_00001_.png).\n"
        "2. Press Run once: 8 images (4 stages x 2 strengths) + a skeleton preview.\n"
        "3. Paste results here, including the skeleton preview.", size=(420, 460))

src = wf.node("LoadImage", (-440, 520), (320, 420), [SOURCE, "image"], outputs=[("IMAGE", "IMAGE"), ("MASK", "MASK")])
pose = wf.node("DWPreprocessor", (-440, 980), (320, 250),
               ["enable", "enable", "disable", 1024, "yolox_l.torchscript.pt", "dw-ll_ucoco_384_bs5.torchscript.pt", "disable"],
               inputs=[("image", "IMAGE")], outputs=[("IMAGE", "IMAGE"), ("POSE_KEYPOINT", "POSE_KEYPOINT")])
prev = wf.node("PreviewImage", (-440, 1270), (320, 420), inputs=[("images", "IMAGE")], title="Skeleton preview")
patch = wf.node("ModelPatchLoader", (0, 620), (320, 58), ["Z-Image-Turbo-Fun-Controlnet-Union.safetensors"], outputs=[("MODEL_PATCH", "MODEL_PATCH")])
cn = wf.node("QwenImageDiffsynthControlnet", (0, 720), (320, 140), [0.8],
             inputs=[("model", "MODEL"), ("model_patch", "MODEL_PATCH"), ("vae", "VAE"), ("image", "IMAGE")], outputs=[("MODEL", "MODEL")],
             title="Pose lock (strength)")
enc = wf.node("VAEEncode", (0, 900), (200, 46), inputs=[("pixels", "IMAGE"), ("vae", "VAE")], outputs=[("LATENT", "LATENT")])
wf.link(src, 0, pose, "image"); wf.link(pose, 0, prev, "images")
wf.link(wf.unet, 0, cn, "model"); wf.link(patch, 0, cn, "model_patch"); wf.link(wf.vae, 0, cn, "vae"); wf.link(pose, 0, cn, "image")
wf.link(cn, 0, wf.msaf, "model")
wf.link(src, 0, enc, "pixels"); wf.link(wf.vae, 0, enc, "vae")

for row, d in enumerate(DENOISES):
    y = row * 600
    for col, (tag, build) in enumerate(STAGES):
        wf.t2i(prompt(build), SEED, f"define/r3_{tag}_d{int(d * 100)}", (400 + col * 1150, y), f"{tag} denoise {d}", latent=enc, denoise=d)
    wf.group(f"Denoise {d}", (380, y - 50, 4600, 580))
wf.save(pathlib.Path(__file__).resolve().parents[1] / "workflows" / "03_pose_locked_ladder.json")
