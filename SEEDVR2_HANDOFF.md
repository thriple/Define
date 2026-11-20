# SeedVR2 Upscaler — handoff

Paste this into a new chat, and attach your copy of `SeedVR2-Upscaler.zip`. The new chat can't see the files from the old one.

## What it is
This is a small Windows drag-and-drop app that upscales videos with **SeedVR2**, using the ComfyUI that Pinokio installed. It runs SeedVR2's command-line tool directly, so Pinokio and ComfyUI don't need to be open.

The app is in `SeedVR2-Upscaler.zip`:

| File | What it does |
|---|---|
| `SeedVR2-Upscaler.ps1` | The app itself (about 525 lines of PowerShell). It runs the upscaler in the background and keeps the window responsive. |
| `SeedVR2 Upscaler.bat` | Opens the app without a console window. |
| `SeedVR2 Upscaler (show errors).bat` | Opens the app with a console window, so error messages stay visible. |
| `README.txt` | Usage notes. |

Settings are saved to `%APPDATA%\SeedVR2Upscaler\settings.json`.

There's also an earlier ComfyUI workflow, `SeedVR2_video_upscale_8GB.json`. It loads every frame into RAM, which you objected to, so it was replaced by the app, which streams frames in chunks.

## Your machine
- RTX 3050 with 8 GB of video memory, and 32 GB of RAM. Windows.
- ComfyUI folder (hardcoded at your request; the install never moves): `S:\pinokio\api\comfy.git`
  - Upscaler node: `app\custom_nodes\seedvr2_videoupscaler\inference_cli.py`. The folder name differs from the GitHub repo name `ComfyUI-SeedVR2_VideoUpscaler`; the app checks for both.
  - Python: `app\env\Scripts\python.exe` (you confirmed this one).
  - Models: `app\models\SEEDVR2`. It holds `ema_vae_fp16`, `seedvr2_ema_3b_fp8_e4m3fn`, `seedvr2_ema_3b-Q8_0.gguf` and `seedvr2_ema_7b_fp16`.
- ffmpeg: `S:\pinokio\bin\ffmpeg-env`. It's used to read and write video and to copy the audio back in.

## The command the app runs
```
python inference_cli.py <video> --output <out> --output_format mp4
  --dit_model <model> --model_dir <models folder>
  --resolution <short edge> --max_resolution <max long edge>
  --batch_size <frames per batch> --uniform_batch_size
  --temporal_overlap <overlap> --chunk_size <frames held in RAM>
  --color_correction lab
  --dit_offload_device cpu --vae_offload_device cpu --tensor_offload_device cpu
  --vae_encode_tiled --vae_encode_tile_size <tile> --vae_encode_tile_overlap <tile/4>
  --vae_decode_tiled --vae_decode_tile_size <tile> --vae_decode_tile_overlap <tile/4>
  --input_noise_scale <noise> --cache_dit --cache_vae
  [--blocks_to_swap N --swap_io_components] [--video_backend ffmpeg] [--debug] [--load_cap N (preview only)]
```

Defaults:
- Model: 3B Q8_0.
- Short edge 1080, long edge capped at 1920.
- Frames per batch: 5.
- Frames held in RAM: 60. Overlap: 2 frames.
- Blocks swapped to RAM: 32. Input noise: 0. Tile size: 512.
- Preview: on, first 90 frames only.

The output file is named `<name>_seedvr2_1080p.mp4`. In preview mode, the settings are added to the file name.

## Problems fixed so far
1. **"Not found":** caused by the node folder name mismatch above. Fixed by checking for both names.
2. **Models not found:** the app wasn't looking in your models folder. Added a "Models folder..." button and a label showing where each model was found. It now reuses your existing Q8 file.
3. **Slow searches of your drives:** replaced by the hardcoded `S:\pinokio\api\comfy.git`.
4. **Arguments not passed:** a variable was named `$args`, which PowerShell reserves. Renamed to `$argStr`.

## Where it stands
The app runs, but **quality is poor: artifacts, and a slightly jittery look between frames.** That was at the defaults, with batch 5 and the 3B Q8 model.

Suggested side-by-side test, using preview mode and comparing the files by name:

| What | Why |
|---|---|
| Frames per batch **21**, short edge **720** | Bigger batches keep frames consistent with each other, which means less jitter. The lower resolution keeps memory down. |
| Input noise **0.15** (try 0.1–0.3) | Reduces made-up texture and artifacts. |
| Model **3b_fp8** instead of Q8 gguf | The gguf versions can lose quality; fp8 is often cleaner. |
| Model **7b_fp16**, blocks swapped **36** | Best quality, but slow on 8 GB. |
| Tile size **768 or 1024** | Fewer seams between tiles, uses more memory. |

**Waiting on:** your results from a 3-second 540x960 clip upscaled to 1080. Your videos come in various sizes, and you left the default 1080 / 1920 settings.

## Ideas if the test doesn't fix it
- Match the batch size to the shot length, or use batches of 4n+1 frames: 21, 33, 45 and so on.
- Raise the overlap to 4–8 frames to hide seams between batches.
- Upscale less per pass, for example 540 → 720 first, or downscale very sharp sources before upscaling.
- Try the "sharp" 7B versions.
- Turn on the verbose log to check whether memory pressure is forcing smaller batches or heavy swapping.
