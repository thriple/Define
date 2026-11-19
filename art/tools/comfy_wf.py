"""Small helper for writing ComfyUI UI-format workflows with Z-Image Turbo text-to-image chains."""
import json, pathlib

STYLE_3D = ("Stylized 3D animated feature film character render, soft studio lighting, slightly glossy suit "
            "material, appealing stylized proportions, high-end animation studio quality.")
POSE = ("standing facing the viewer in a confident heroic stance, feet shoulder-width apart, arms relaxed "
        "and held slightly away from his body, the whole figure visible from head to toe with empty space "
        "around it. ")
END = " Plain flat pale grey background, no scenery, no text, no logos, no watermark."


class Workflow:
    def __init__(self, width=832, height=1216, link_model=True):
        self.nodes, self.links, self.groups = [], [], []
        self._lid = 0
        self._nid = 100
        self.unet = self.node("UNETLoader", (0, 0), (320, 82), ["z_image_turbo_bf16.safetensors", "default"], outputs=[("MODEL", "MODEL")], nid=2)
        self.clip = self.node("CLIPLoader", (0, 120), (320, 106), ["qwen_3_4b.safetensors", "lumina2", "default"], outputs=[("CLIP", "CLIP")], nid=3)
        self.vae = self.node("VAELoader", (0, 270), (320, 58), ["ae.safetensors"], outputs=[("VAE", "VAE")], nid=4)
        self.msaf = self.node("ModelSamplingAuraFlow", (0, 370), (320, 58), [3], inputs=[("model", "MODEL")], outputs=[("MODEL", "MODEL")], nid=5)
        self.latent = self.node("EmptySD3LatentImage", (0, 470), (320, 106), [width, height, 1], outputs=[("LATENT", "LATENT")], nid=6)
        if link_model:
            self.link(self.unet, 0, self.msaf, "model")

    def node(self, ntype, pos, size, widgets=None, inputs=(), outputs=(), title=None, nid=None):
        if nid is None:
            nid = self._nid
            self._nid += 1
        n = {"id": nid, "type": ntype, "pos": list(pos), "size": list(size), "flags": {}, "order": len(self.nodes), "mode": 0,
             "inputs": [{"name": a, "type": b, "link": None} for a, b in inputs],
             "outputs": [{"name": a, "type": b, "links": [], "slot_index": i} for i, (a, b) in enumerate(outputs)],
             "properties": {"Node name for S&R": ntype}, "widgets_values": widgets if widgets is not None else []}
        if title:
            n["title"] = title
        self.nodes.append(n)
        return n

    def link(self, src, sslot, dst, dname):
        self._lid += 1
        tp = src["outputs"][sslot]["type"]
        src["outputs"][sslot]["links"].append(self._lid)
        names = [i["name"] for i in dst["inputs"]]
        inp = dst["inputs"][names.index(dname)]
        assert inp["type"] == tp, (dname, inp["type"], tp)
        inp["link"] = self._lid
        self.links.append([self._lid, src["id"], sslot, dst["id"], names.index(dname), tp])

    def note(self, text, pos=(-440, 0), size=(400, 420)):
        self.node("Note", pos, size, [text], nid=1)

    def t2i(self, prompt, seed, prefix, pos, title, latent=None, denoise=1):
        x, y = pos
        te = self.node("CLIPTextEncode", (x, y), (420, 300), [prompt], inputs=[("clip", "CLIP")], outputs=[("CONDITIONING", "CONDITIONING")], title=title)
        zo = self.node("ConditioningZeroOut", (x, y + 340), (240, 26), inputs=[("conditioning", "CONDITIONING")], outputs=[("CONDITIONING", "CONDITIONING")])
        ks = self.node("KSampler", (x + 440, y), (300, 262), [seed, "fixed", 8, 1, "res_multistep", "simple", denoise],
                       inputs=[("model", "MODEL"), ("positive", "CONDITIONING"), ("negative", "CONDITIONING"), ("latent_image", "LATENT")], outputs=[("LATENT", "LATENT")])
        de = self.node("VAEDecode", (x + 440, y + 300), (200, 46), inputs=[("samples", "LATENT"), ("vae", "VAE")], outputs=[("IMAGE", "IMAGE")])
        sv = self.node("SaveImage", (x + 760, y), (340, 500), [prefix], inputs=[("images", "IMAGE")])
        self.link(self.clip, 0, te, "clip"); self.link(te, 0, zo, "conditioning")
        self.link(self.msaf, 0, ks, "model"); self.link(te, 0, ks, "positive"); self.link(zo, 0, ks, "negative"); self.link(latent or self.latent, 0, ks, "latent_image")
        self.link(ks, 0, de, "samples"); self.link(self.vae, 0, de, "vae"); self.link(de, 0, sv, "images")

    def group(self, title, bounding):
        self.groups.append({"title": title, "bounding": list(bounding), "color": "#3f789e", "font_size": 24})

    def save(self, path):
        ids = {n["id"]: n for n in self.nodes}
        for lid, o, os_, t, ts, _ in self.links:
            assert lid in ids[o]["outputs"][os_]["links"] and ids[t]["inputs"][ts]["link"] == lid
        assert all(i["link"] is not None for n in self.nodes for i in n["inputs"])
        wf = {"last_node_id": max(ids), "last_link_id": self._lid, "nodes": self.nodes, "links": self.links,
              "groups": self.groups, "config": {}, "extra": {"ds": {"scale": 0.5, "offset": [500, 100]}}, "version": 0.4}
        pathlib.Path(path).write_text(json.dumps(wf, indent=1))
        print("wrote", path, len(self.nodes), "nodes", len(self.links), "links")
