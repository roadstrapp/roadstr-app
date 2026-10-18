# Offline voice search

The microphone button in search can recognise speech **on the phone**, with no audio leaving it. This page
describes what happens, what it needs, how it fails, and — separately — what has and has not been measured.

## What happens when you tap the microphone

```
tap ─▶ model installed and language known? ──no──▶ the phone's own recogniser (as before)
              │ yes
              ▼
      microphone permission? ──denied──▶ the phone's own recogniser
              │ granted
              ▼
   listening card ─▶ record until you stop talking (or tap Done) ─▶ recognise ─▶ text in the search box
              │                                                       │
              └─ cannot open the microphone / not enough memory /     └─ nothing heard: a short message
                 engine cannot load: the phone's own recogniser
```

While you talk the model is loaded in parallel, so there is no wait once you stop. If loading fails, Roadstr notices
while you are still talking and hands over at once.

## What it needs

| | |
|---|---|
| CPU | 64-bit Arm (`arm64-v8a`). Other ABIs ship without the engine. |
| RAM | 3 GB in total to be offered at all; 1.5 GB free at the moment the model loads. Both are conservative estimates. |
| Storage | 485 MB for the model, plus a 64 MB margin while it downloads. |
| Android | 7.0 (API 24) or newer. |
| Permission | `RECORD_AUDIO`, asked the first time, and only if the model is installed. |

The engine has two builds. A tiny probe reads the CPU's feature bits and picks the faster one (Armv8.2 dot-product + fp16) only
when both are present; otherwise the baseline Armv8-A build runs. If the probe cannot load, the baseline is used.

## The model

NVIDIA Parakeet TDT 0.6B v3, as a Q4_K_M GGUF conversion by handy-computer. Languages: bg hr cs da nl en et fi fr de el hu it
lv lt mt pl pt ro sk sl es sv ru uk. Licence CC-BY-4.0; the attribution is shown in *Settings → Voice search* and in
[THIRD_PARTY_NOTICES.md](../THIRD_PARTY_NOTICES.md).

* **Download.** Only from *Settings → Voice search*, only over HTTPS, from an address that names an exact upstream commit. A
  redirect from HTTPS to HTTP is refused. The transfer resumes after an interruption (HTTP `Range`) and can be cancelled.
* **Integrity.** The bytes go to a `.part` file; only when the size and the SHA-256 both equal the values compiled into the app is
  the file renamed into place. A wrong file is deleted, never loaded. A later start trusts a marker (size + modification time)
  instead of hashing 485 MB again, and rehashes if the file changed.
* **Where.** The app's private no-backup storage. *Delete the model* removes it, the partial file and the marker.
* **Nothing is downloaded by itself.** Not at install, not on Wi-Fi, not in the background.

## Listening

Roadstr finds the end of what you say by itself: it learns how loud the room is, treats a run of frames about 2.2 times louder
as speech, keeps 0.3 s before the start so the first syllable is not clipped, and stops after 1.2 s of silence. It gives up after
8 s without speech and cuts an utterance off at 20 s. In a noisy car silence may never come, so the card has a **Done** button.

## Memory and battery

The loaded model takes about a gigabyte. Roadstr keeps at most one engine, drops it one minute after the last use, and drops it
immediately when Android reports that it is running low on memory. At most two inference threads are used, on the fastest cores
(more threads saved little time and cost much more CPU on the phone the figure comes from). The microphone is open only while the
listening card is on screen.

## Privacy

* The audio is processed in memory by the engine and never written to disk, sent, or logged.
* The engine code does not use the network; the only request is the model download, which `huggingface.co` sees as an IP
  address and a file request. `test/native_offline_speech_contract_test.dart` checks that the recorder and the engine know nothing
  about the network classes.
* When it falls back to the phone's recogniser, whatever that recogniser does with audio is up to the phone's vendor, exactly
  as before.

## How it fails

| What goes wrong | What the user gets |
|---|---|
| Model not installed, or language unknown to it | The phone's own recogniser |
| Permission denied | The phone's own recogniser |
| Microphone busy or unavailable | The phone's own recogniser |
| Not enough free memory to load | The phone's own recogniser (and the engine is tried again next time) |
| The engine cannot load or run the model | The phone's own recogniser; Settings shows the model as unusable until the app restarts or it is deleted |
| Nothing said | "I did not hear anything." |
| Download interrupted | A message; tap download again to resume |
| Download does not match the checksum | The file is discarded; a message says so |
| Not enough storage | A message; nothing is written |

## Where the code is

| | |
|---|---|
| `core/speech/` | Model contract (`SpeechModelCatalog`), end-of-speech detector (`UtteranceEndpointer`), query clean-up (`SpokenQuery`), result types. Pure Kotlin. |
| `service/speech/` | Model store and HTTPS transport, engine lifetime and memory gate (`SpeechEngineProvider`), the recording → recognising flow (`SpeechRecognitionRunner`), the Android microphone and gateway. |
| `feature/speech/` | The gateway interface, the listening card, the Settings mapping. |
| `offlinestt/` | The engine's Kotlin API, vendored unchanged ([third_party/offline-stt-engine](../third_party/offline-stt-engine/README.md)). |
| `NativeRoadTestActivity` | The permission request, the call, and the fall back to the phone's recogniser. |

## Updating the engine or the model

1. Change the engine in `roadstrapp/offline-speech-to-text` (`engine/`), build with `engine/scripts/build-android.sh`, commit.
2. Copy the Kotlin sources and the four libraries into `android/app/src/main/`, update `third_party/offline-stt-engine/SOURCE_COMMIT`
   and `SHA256SUMS`, and the digests in `android/app/build.gradle.kts` (`bundledNativeChecksums`).
3. Run `third_party/offline-stt-engine/rebuild.sh`; it must end with *reproducible*.
4. For a different model, change `SpeechModelCatalog` **and** the table in `third_party/offline-stt-engine/README.md` and the
   notices; `test/native_offline_speech_contract_test.dart` fails if they disagree.

## What was measured, and what was not

Measured, on a 32-thread x86-64 desktop with the real model and real engine code (not the Android build), on speech
synthesised by Kokoro (two Italian voices, 16 kHz):

| Said | Heard |
|---|---|
| Via Giuseppe Garibaldi, Bologna | Via Giuseppe Garibaldi Bologna |
| Stazione di Firenze Santa Maria Novella | Stazione di Firenze Santa Maria Novella |
| Piazza del Duomo, Milano | Piazza del Duomo Milano |
| Ristorante pizzeria vicino a me | Ristorante e pizzeria vicino a me |
| Corso Vittorio Emanuele secondo, Torino | Corso Vittorio Emanuele II Torino |
| Distributore di benzina più vicino | Distributore di **benzena** più vicino |
| Portami a casa | **Porta-me** a casa |
| Navigami verso Viale dei Mille a Napoli | Navigami verso Viale dei Mille a Napoli |

Sixteen runs (eight phrases, two voices): both voices gave the same text on all but the *Ristorante* and *Porta-me* lines. Loading
took about 0.4–0.5 s and a 2–3 s phrase decoded in 0.25–0.4 s on that desktop.

The Android build was checked structurally: it compiles; the dot-product build contains `sdot`/fp16 instructions and the baseline
contains none; every library is aligned for 16 KB pages; the JNI symbols are exported; R8 keeps the native methods; rebuilding
from a clean clone gives identical bytes.

**Not measured, and therefore unknown:**

* Decode time, peak memory and battery use **on a phone**. Expect it to be several times slower than the desktop figures.
* Recognition with a **real microphone**, real accents, a moving car, wind, a radio. Synthetic speech is clean and uniform.
* Whether the memory thresholds are right for real devices.
* The end-of-speech settings with real background noise: they pass synthetic tests only.
* The listening card on screen, and the permission dialog, on a device.

Until someone tries it on a phone, treat the feature as experimental.
