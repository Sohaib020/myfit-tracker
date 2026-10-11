"""Free image -> 3D via the public TRELLIS demo (MIT licence) on Hugging Face, called from CI.
usage: python trellis.py <image> <out.glb>   (env HF_SPACE to override; prints the API on first failure)"""
import sys, os, shutil, time
from gradio_client import Client, handle_file
img, out = sys.argv[1], sys.argv[2]
spaces = [s for s in os.environ.get("HF_SPACE", "trellis-community/TRELLIS,JeffreyXiang/TRELLIS").split(",") if s]
last = None
for sp in spaces:
    try:
        c = Client(sp, verbose=False, hf_token=os.environ.get("HF_TOKEN") or None)
        print("SPACE", sp)
        try: c.predict(api_name="/start_session")
        except Exception as e: print("start_session:", str(e)[:120])
        pre = c.predict(image=handle_file(img), api_name="/preprocess_image")
        print("preprocessed", pre)
        t = time.time()
        res = c.predict(image=handle_file(pre if isinstance(pre, str) else img), multiimages=[], seed=7, ss_guidance_strength=7.5,
                        ss_sampling_steps=12, slat_guidance_strength=3, slat_sampling_steps=12, multiimage_algo="stochastic",
                        mesh_simplify=0.9, texture_size=2048, api_name="/generate_and_extract_glb")
        print("generate_and_extract_glb", round(time.time() - t), "s", res)
        g = res[2] or res[1]
        try:
            v = res[0]["video"] if isinstance(res[0], dict) else res[0]
            shutil.copy(v, out[:-4] + "_preview.mp4")
        except Exception as e: print("no video", e)
        shutil.copy(g, out); print("GLB_OK", os.path.getsize(out)); sys.exit(0)
    except SystemExit: raise
    except Exception as e:
        last = e; print("FAIL", sp, repr(e)[:400])
        try: print(Client(sp, verbose=False).view_api(return_format="str")[:3000])
        except Exception as e2: print("view_api fail", e2)
sys.exit(1)
