#!/usr/bin/env python3
"""Generate bundled WAV files for Kokoro TTS phrases.

Pre-generates the 14 fixed navigation phrases (letsgo + arrived × 7 languages)
so the app can play them from AudioSource.asset() with ~0ms latency instead of
running on-device inference (which can take 2-3 seconds on first call).

Usage:
    # Download model files automatically and generate WAVs:
    python3 tools/generate_kokoro_phrases.py

    # Use model files already pulled from the device with adb:
    #   adb pull /data/user/0/com.example.roadstr/app_flutter/kokoro/ kokoro_models/
    python3 tools/generate_kokoro_phrases.py --models-dir kokoro_models/

Requirements (pip install):
    onnxruntime==1.20.1 numpy requests

System requirements:
    espeak-ng  (apt install espeak-ng  /  brew install espeak)

Notes on CJK phonemization:
    The bundled espeak-ng-data does not have readings for arbitrary kanji or
    hanzi.  Japanese phrases are therefore expressed in kana (katakana/hiragana),
    and Chinese phrases in tone-number pinyin (e.g. "chu1fa1").  Both formats
    produce correct IPA with the cmn/ja espeak-ng voices included in the app.
"""

import argparse
import gzip
import json
import struct
import subprocess
import sys
import tarfile
import tempfile
from pathlib import Path

try:
    import numpy as np
except ImportError:
    sys.exit("pip install numpy")
try:
    import onnxruntime as ort
except ImportError:
    sys.exit("pip install onnxruntime")
try:
    import requests
except ImportError:
    sys.exit("pip install requests")

# ── Static config (mirrors kokoro_voices.dart) ────────────────────────────────

REPO_BASE = "https://huggingface.co/onnx-community/Kokoro-82M-v1.0-ONNX/resolve/main"
MODEL_FILE_REL = "onnx/model_q8f16.onnx"
TOKENIZER_FILE_REL = "tokenizer.json"
MODEL_SIZE = 86033585
VOICE_SIZE = 522240

VOICES = {
    "en": {"f": "af_heart",   "m": "am_michael"},
    "it": {"f": "if_sara",    "m": "im_nicola"},
    "es": {"f": "ef_dora",    "m": "em_alex"},
    "fr": {"f": "ff_siwis"},  # no male voice shipped for French in this model
    "ja": {"f": "jf_alpha",   "m": "jm_kumo"},
    "zh": {"f": "zf_xiaobei", "m": "zm_yunxi"},
    "pt": {"f": "pf_dora",    "m": "pm_alex"},
}

# Voice names for espeak-ng (mirrors EspeakPhonemizer._voiceForLang)
ESPEAK_VOICES = {
    "en": "en-us",
    "it": "it",
    "es": "es",
    "fr": "fr",
    "ja": "ja",
    "zh": "cmn",
    "pt": "pt-BR",
}

# Flutter asset containing the espeak-ng data directory (relative to project root).
ESPEAK_DATA_ASSET = Path("assets/espeak-ng-data.tar.gz")

# Phrases for phonemization.  CJK notes:
#   ja — kanji not in the bundled espeak-ng-data; use kana transliteration.
#   zh — hanzi not supported; use tone-number pinyin (e.g. "chu1fa1").
# These inputs produce correct IPA; the text shown to the user in the app is
# unchanged (kokoro_tts_service.dart still uses the original CJK strings).
PHRASES = {
    "letsgo": {
        "en": "Let's go!",
        "it": "Partenza",
        "es": "¡Vamos!",
        "fr": "C'est parti !",
        "ja": "シュッパツします！",       # 出発します！ in kana
        "zh": "chu1fa1",                   # 出发！ in tone-number pinyin
        "pt": "Vamos lá!",
    },
    "arrived": {
        "en": "You have arrived at your destination.",
        "it": "Sei arrivato a destinazione.",
        "es": "Has llegado a tu destino.",
        "fr": "Vous êtes arrivé à destination.",
        "ja": "もくてきちにとうちゃくしました。",  # 目的地に到着しました。in hiragana
        "zh": "nin3yi3dao4da2 mu4di4di4",    # 您已到达目的地。in pinyin
        "pt": "Chegou ao seu destino.",
    },
}

SAMPLE_RATE = 24000

# ── Download ──────────────────────────────────────────────────────────────────

def _download(url: str, dest: Path) -> None:
    dest.parent.mkdir(parents=True, exist_ok=True)
    print(f"  → {dest.name}  ({url.split('/')[-1]})")
    r = requests.get(url, stream=True, timeout=120)
    r.raise_for_status()
    total = int(r.headers.get("content-length", 0))
    done = 0
    with open(dest, "wb") as f:
        for chunk in r.iter_content(65536):
            f.write(chunk)
            done += len(chunk)
            if total:
                bar = "#" * (done * 30 // total)
                print(f"\r    [{bar:<30}] {done * 100 // total}%", end="", flush=True)
    print()


def ensure_file(path: Path, url: str, expected_size: int | None = None) -> None:
    if path.exists() and (expected_size is None or path.stat().st_size == expected_size):
        print(f"  ✓  {path.name}")
        return
    _download(url, path)

# ── Tokenizer ─────────────────────────────────────────────────────────────────

def load_vocab(tokenizer_path: Path) -> dict[str, int]:
    data = json.loads(tokenizer_path.read_text())
    model = data.get("model", {})
    if isinstance(model, dict) and "vocab" in model:
        raw = model["vocab"]
    else:
        raw = {k: v for k, v in data.items() if isinstance(v, int)}
    return {k: int(v) for k, v in raw.items()}


def tokenize(ipa: str, vocab: dict[str, int]) -> list[int]:
    """Map IPA chars to token IDs, wrap with BOS/EOS (ID 0)."""
    inner = [vocab[c] for c in ipa if c in vocab]
    return [0] + inner + [0]

# ── Phonemization ─────────────────────────────────────────────────────────────

def phonemize(text: str, lang: str, espeak_data_path: str | None = None) -> str:
    voice = ESPEAK_VOICES.get(lang, "en-us")
    cmd = ["espeak-ng"]
    if espeak_data_path:
        cmd += ["--path", espeak_data_path]
    cmd += ["-v", voice, "--ipa", "-q", text]
    result = subprocess.run(cmd, capture_output=True, text=True)
    if result.returncode != 0:
        raise RuntimeError(
            f"espeak-ng failed (lang={lang}): {result.stderr.strip()}\n"
            "Install with:  apt install espeak-ng  /  brew install espeak"
        )
    ipa = result.stdout.replace("\n", " ").strip()
    return ipa


def extract_espeak_data(asset_path: Path, dest_dir: Path) -> str:
    """Extract espeak-ng-data.tar.gz to dest_dir, return parent path for --path flag."""
    if (dest_dir / "espeak-ng-data").exists():
        return str(dest_dir)
    print(f"  Extracting {asset_path.name} to {dest_dir}…")
    with tarfile.open(asset_path, "r:gz") as tf:
        tf.extractall(dest_dir)
    return str(dest_dir)

# ── WAV writing ───────────────────────────────────────────────────────────────

def float32_to_wav(samples: np.ndarray, sample_rate: int) -> bytes:
    pcm = np.clip(samples * 32767, -32768, 32767).astype(np.int16)
    data = pcm.tobytes()
    data_size = len(data)
    header = struct.pack(
        "<4sI4s4sIHHIIHH4sI",
        b"RIFF",
        36 + data_size,
        b"WAVE",
        b"fmt ",
        16,
        1,                      # PCM
        1,                      # mono
        sample_rate,
        sample_rate * 2,        # byte rate (16-bit mono)
        2,                      # block align
        16,                     # bits per sample
        b"data",
        data_size,
    )
    return header + data

# ── Main ──────────────────────────────────────────────────────────────────────

def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument(
        "--models-dir",
        type=Path,
        default=Path("kokoro_models"),
        help="Directory with model_q8f16.onnx, tokenizer.json, and *.bin voice files (auto-downloaded if missing)",
    )
    parser.add_argument(
        "--espeak-data",
        type=Path,
        default=None,
        help="Parent directory of espeak-ng-data/ (default: auto-extract from assets/espeak-ng-data.tar.gz)",
    )
    args = parser.parse_args()

    models_dir: Path = args.models_dir
    out_dir = Path("assets/kokoro_phrases")
    out_dir.mkdir(parents=True, exist_ok=True)

    # ── espeak-ng data path ───────────────────────────────────────────────────
    espeak_data: str | None = None
    if args.espeak_data:
        espeak_data = str(args.espeak_data)
        print(f"── espeak-ng-data: {espeak_data} ──")
    elif ESPEAK_DATA_ASSET.exists():
        tmp = Path(tempfile.mkdtemp(prefix="roadstr_espeak_"))
        espeak_data = extract_espeak_data(ESPEAK_DATA_ASSET, tmp)
        print(f"── espeak-ng-data: bundled ({ESPEAK_DATA_ASSET}) ──")
    else:
        print("── espeak-ng-data: system default (bundled asset not found) ──")

    # ── 1. Ensure model files ─────────────────────────────────────────────────
    print("── Model files ──")
    model_path = models_dir / "model_q8f16.onnx"
    tok_path = models_dir / "tokenizer.json"
    ensure_file(model_path, f"{REPO_BASE}/{MODEL_FILE_REL}", MODEL_SIZE)
    ensure_file(tok_path, f"{REPO_BASE}/{TOKENIZER_FILE_REL}")
    for genders in VOICES.values():
        for voice in genders.values():
            ensure_file(
                models_dir / f"{voice}.bin",
                f"{REPO_BASE}/voices/{voice}.bin",
                VOICE_SIZE,
            )

    # ── 2. Load tokenizer + ONNX session ──────────────────────────────────────
    print("\n── Loading model ──")
    vocab = load_vocab(tok_path)
    print(f"  vocab size: {len(vocab)}")

    sess_opts = ort.SessionOptions()
    sess_opts.intra_op_num_threads = 4
    session = ort.InferenceSession(
        str(model_path),
        sess_opts,
        providers=["CPUExecutionProvider"],
    )
    input_names = [i.name for i in session.get_inputs()]
    print(f"  inputs: {input_names}")

    # ── 3. Generate WAVs ──────────────────────────────────────────────────────
    print("\n── Generating phrases ──")
    errors: list[str] = []

    expected_count = sum(len(VOICES[lang]) for lang in PHRASES["letsgo"]) * len(PHRASES)

    for phrase_id, texts in PHRASES.items():
        for lang, text in texts.items():
            for gender, voice_name in VOICES[lang].items():
                out_path = out_dir / f"{lang}_{gender}_{phrase_id}.wav"
                label = f"{lang}_{gender}_{phrase_id}"

                if out_path.exists():
                    print(f"  ✓  {label}  (already exists)")
                    continue

                print(f"  {label}  \"{text}\"")

                # Phonemize
                try:
                    ipa = phonemize(text, lang, espeak_data)
                except Exception as e:
                    msg = f"    SKIP {label}: {e}"
                    print(msg)
                    errors.append(msg)
                    continue
                print(f"    IPA: {ipa}")

                # Tokenize
                token_ids = tokenize(ipa, vocab)
                n_inner = len(token_ids) - 2  # without BOS/EOS
                print(f"    tokens: {n_inner} (+ BOS/EOS = {len(token_ids)})")

                # Voice embedding — row at min(n_inner, 509) of the 510×256 matrix
                voice_data = np.frombuffer(
                    (models_dir / f"{voice_name}.bin").read_bytes(),
                    dtype=np.float32,
                )
                style_idx = min(n_inner, 509)
                style = voice_data[style_idx * 256 : style_idx * 256 + 256].reshape(1, 256)

                # ONNX inference
                outputs = session.run(
                    None,
                    {
                        "input_ids": np.array([token_ids], dtype=np.int64),
                        "style": style.astype(np.float32),
                        "speed": np.array([1.0], dtype=np.float32),
                    },
                )
                audio = outputs[0].flatten().astype(np.float32)
                duration = len(audio) / SAMPLE_RATE
                print(f"    audio: {len(audio)} samples ({duration:.2f}s)")

                # Write WAV
                wav_bytes = float32_to_wav(audio, SAMPLE_RATE)
                out_path.write_bytes(wav_bytes)
                print(f"    saved: {out_path}  ({len(wav_bytes) / 1024:.1f} KB)")

    # ── Summary ───────────────────────────────────────────────────────────────
    wav_files = sorted(out_dir.glob("*.wav"))
    total_kb = sum(f.stat().st_size for f in wav_files) / 1024
    print(f"\n── Done: {len(wav_files)}/{expected_count} WAV files, {total_kb:.0f} KB total ──")

    if errors:
        print("\nErrors (re-run after fixing):")
        for e in errors:
            print(e)
        sys.exit(1)

    if len(wav_files) == expected_count:
        print("\nBuild the APK — the WAVs are now bundled in assets/kokoro_phrases/")
        print("Run:  flutter build apk --release")


if __name__ == "__main__":
    main()
