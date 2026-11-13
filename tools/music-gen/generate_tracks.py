"""
HAIDA 데모 음원 만들기 (기능 3).

저작권 걱정 없이 시연하려고 곡을 직접 '작곡'한다. 외부 음원 · 샘플을 쓰지 않고 수식으로 소리를 만든다.
같은 시드면 같은 곡이 나온다(재현 가능). 만든 곡은 이 저장소의 것이라 자유롭게 써도 된다.

- 분위기(작곡 의도)가 템포 · 장/단조 · 화음 · 드럼 밀도 · 소리 크기를 정하고
- 가수(장르)가 리드 악기 음색을 정한다(장르 선호도 학습이 소리로도 구별되게).

앱은 이 '작곡 의도'를 쓰지 않는다. 앱 안의 분석기(music/analysis)가 소리만 듣고 분위기를 다시 판단한다.
tracks.json 의 intent 는 분석 결과가 맞는지 비교하는 용도로만 남긴다.

쓰는 법(저장소 루트에서):  python tools/music-gen/generate_tracks.py
필요: Python 3.10+, numpy, ffmpeg(AAC 인코더). 출력: app/src/main/assets/music/*.m4a, tracks.json
"""
import json
import os
import subprocess
import sys
import tempfile
import wave

import numpy as np

SR = 22050
SECONDS = 60
OUT = os.path.join(os.path.dirname(__file__), "..", "..", "app", "src", "main", "assets", "music")

# 분위기별 작곡 설정. degrees = 마디마다 화음의 근음(음계 도수, 0 = 으뜸음), seventh = 7화음 여부
MOODS = {
    "FRESH": dict(tempo=114, minor=False, degrees=[0, 4, 5, 3], seventh=False, drums="pop", hats=8, loud=-15.0, bright=1.0),
    "DRIVE": dict(tempo=128, minor=False, degrees=[0, 5, 3, 4], seventh=False, drums="four", hats=16, loud=-11.0, bright=1.25),
    "CRUISE": dict(tempo=95, minor=False, degrees=[0, 3, 1, 4], seventh=True, drums="laid", hats=8, loud=-17.0, bright=0.8),
    "RAIN": dict(tempo=80, minor=True, degrees=[0, 5, 3, 4], seventh=False, drums="rim", hats=0, loud=-20.0, bright=0.55, rain=True),
    "SUNSET": dict(tempo=87, minor=False, degrees=[3, 4, 2, 5], seventh=True, drums="laid", hats=8, loud=-18.0, bright=0.7),
    "NIGHT": dict(tempo=66, minor=True, degrees=[0, 3, 5, 4], seventh=True, drums="none", hats=0, loud=-22.0, bright=0.4),
}

# 가수 = 장르 = 리드 음색 (앱의 장르 선호도와 같은 이름)
ARTISTS = {
    "하루결": dict(genre="어쿠스틱", lead="pluck"),
    "소리결": dict(genre="인디", lead="keys"),
    "Nova Lane": dict(genre="팝", lead="saw"),
    "오렌지 아워": dict(genre="시티팝", lead="ep"),
    "달빛 정원": dict(genre="발라드", lead="pad"),
}

# 팀 UI 프로토타입의 곡 이름 중 분위기마다 3곡
TRACKS = [
    ("FRESH", "첫 햇살 한 모금", "하루결", 2), ("FRESH", "창문을 열면", "오렌지 아워", 7), ("FRESH", "출발선", "Nova Lane", 0),
    ("DRIVE", "풀 스로틀", "Nova Lane", 9), ("DRIVE", "네온 하이웨이", "오렌지 아워", 4), ("DRIVE", "달려라 오늘", "하루결", 2),
    ("CRUISE", "느린 오후", "달빛 정원", 5), ("CRUISE", "바람이 지나는 길", "하루결", 7), ("CRUISE", "해안도로", "소리결", 2),
    ("RAIN", "비 내리는 퇴근길", "소리결", 9), ("RAIN", "와이퍼 박자", "달빛 정원", 4), ("RAIN", "젖은 신호등", "하루결", 11),
    ("SUNSET", "오렌지 하늘", "오렌지 아워", 5), ("SUNSET", "집으로 가는 노을", "하루결", 0), ("SUNSET", "저물녘 드라이브", "소리결", 3),
    ("NIGHT", "가로등 사이로", "달빛 정원", 1), ("NIGHT", "조용한 터널", "소리결", 9), ("NIGHT", "별빛 차선", "Nova Lane", 6),
]

MAJOR = [0, 2, 4, 5, 7, 9, 11]
MINOR = [0, 2, 3, 5, 7, 8, 10]


def hz(midi):
    return 440.0 * 2 ** ((midi - 69) / 12)


def env(n, attack, release_tau):
    t = np.arange(n) / SR
    a = np.minimum(1.0, t / max(attack, 1e-4))
    return a * np.exp(-t / release_tau)


def tone(freq, n, kind, bright):
    """음색 하나. bright 가 클수록 배음이 많다(소리가 밝다)."""
    t = np.arange(n) / SR
    out = np.zeros(n)
    if kind == "saw":
        harms = int(6 + 10 * bright)
        for k in range(1, harms + 1):
            out += np.sin(2 * np.pi * freq * k * t) / k
        return out * env(n, 0.01, 0.35)
    if kind == "pluck":
        harms = int(4 + 8 * bright)
        for k in range(1, harms + 1):
            out += np.sin(2 * np.pi * freq * k * t) * np.exp(-t * (3 + 2.5 * k)) / k
        return out * env(n, 0.003, 1.0)
    if kind == "ep":
        out = np.sin(2 * np.pi * freq * t) + 0.35 * bright * np.sin(2 * np.pi * freq * 2 * t) * np.exp(-t * 6)
        return out * env(n, 0.005, 0.6)
    if kind == "keys":
        out = np.sin(2 * np.pi * freq * t) + 0.25 * bright * np.sin(2 * np.pi * freq * 3 * t) * np.exp(-t * 4)
        return out * env(n, 0.01, 0.5)
    # pad: 느리게 차오르고 길게 남는 화음용 소리
    harms = int(2 + 4 * bright)
    for k in range(1, harms + 1):
        out += np.sin(2 * np.pi * freq * k * t * (1 + 0.002 * (k % 2))) / (k * k)
    attack = min(0.4, n / SR / 3)
    return out * env(n, attack, 2.5)


def add(buf, start, sig, gain):
    end = min(len(buf), start + len(sig))
    if start < end:
        buf[start:end] += sig[: end - start] * gain


def drums(buf, style, hats, beat, bars, rng):
    n_kick = int(0.25 * SR)
    t = np.arange(n_kick) / SR
    kick = np.sin(2 * np.pi * (55 + 90 * np.exp(-t * 30)) * t) * np.exp(-t * 14)
    snare = rng.standard_normal(int(0.18 * SR)) * np.exp(-np.arange(int(0.18 * SR)) / SR * 22)
    hat_raw = rng.standard_normal(int(0.05 * SR))
    hat = np.diff(hat_raw, prepend=0) * np.exp(-np.arange(len(hat_raw)) / SR * 80)
    rim = np.sin(2 * np.pi * 1700 * np.arange(int(0.04 * SR)) / SR) * np.exp(-np.arange(int(0.04 * SR)) / SR * 90)
    for bar in range(bars):
        for b in range(4):
            pos = int((bar * 4 + b) * beat * SR)
            if style == "four":
                add(buf, pos, kick, 0.9)
            elif style in ("pop", "laid") and b in (0, 2):
                add(buf, pos, kick, 0.8 if style == "pop" else 0.6)
            if style in ("pop", "four") and b in (1, 3):
                add(buf, pos, snare, 0.35)
            if style == "laid" and b in (1, 3):
                add(buf, pos, snare, 0.18)
            if style == "rim" and b == 3:
                add(buf, pos, rim, 0.25)
            if hats:
                per = hats // 4
                for h in range(per):
                    add(buf, pos + int(h * beat / per * SR), hat, 0.12 if hats == 8 else 0.09)


def compose(mood, artist, key, seed):
    m = MOODS[mood]
    a = ARTISTS[artist]
    rng = np.random.default_rng(seed)
    beat = 60.0 / m["tempo"]
    bars = int(SECONDS / (4 * beat))
    n = int(SECONDS * SR)
    music = np.zeros(n)
    scale = MINOR if m["minor"] else MAJOR
    root = 48 + key  # C3 근처

    def note(deg, octave=0):
        return root + scale[deg % 7] + 12 * (deg // 7 + octave)

    for bar in range(bars):
        deg = m["degrees"][bar % len(m["degrees"])]
        chord = [note(deg), note(deg + 2), note(deg + 4)] + ([note(deg + 6)] if m["seventh"] else [])
        start = int(bar * 4 * beat * SR)
        bar_len = int(4 * beat * SR)
        # 화음(패드)
        for c in chord:
            add(music, start, tone(hz(c + 12), bar_len, "pad", m["bright"]), 0.10)
        # 베이스
        bass_steps = 8 if mood == "DRIVE" else (2 if mood == "NIGHT" else 4)
        for s in range(bass_steps):
            pos = start + int(s * 4 / bass_steps * beat * SR)
            add(music, pos, tone(hz(note(deg) - 12), int(beat * SR * 4 / bass_steps), "keys", 0.3), 0.32)
        # 리드: 화음 음을 오르내리는 아르페지오. 빠른 분위기일수록 촘촘하다
        steps = 8 if m["tempo"] >= 100 else (4 if m["tempo"] >= 75 else 2)
        pattern = rng.permutation(len(chord)).tolist()
        for s in range(steps):
            pitch = chord[pattern[s % len(pattern)]] + 24 - (12 if mood in ("NIGHT", "RAIN") else 0)
            if rng.random() < 0.15:
                continue
            pos = start + int(s * 4 / steps * beat * SR)
            dur = int(beat * SR * 4 / steps * (1.6 if a["lead"] == "pad" else 1.0))
            add(music, pos, tone(hz(pitch), dur, a["lead"], m["bright"]), 0.16)

    drums(music, m["drums"], m["hats"], beat, bars, rng)

    if m.get("rain"):
        noise = rng.standard_normal(n)
        # 낮은 쪽만 남긴 빗소리(이동 평균)
        rain = np.convolve(noise, np.ones(8) / 8, mode="same") * 0.05
        music += rain

    # 짧은 메아리(잔향 흉내)
    for delay, g in ((0.11, 0.25), (0.23, 0.15), (0.37, 0.08)):
        d = int(delay * SR)
        music[d:] += music[:-d] * g

    # 앞뒤 2초 페이드
    fade = int(2 * SR)
    music[:fade] *= np.linspace(0, 1, fade)
    music[-fade:] *= np.linspace(1, 0, fade)

    # 분위기별 소리 크기(RMS dBFS)로 맞춘다
    rms = np.sqrt(np.mean(music ** 2)) + 1e-9
    music *= 10 ** (m["loud"] / 20) / rms
    peak = np.max(np.abs(music))
    if peak > 0.98:
        music *= 0.98 / peak
    return music


def write_wav(path, x):
    pcm = (np.clip(x, -1, 1) * 32767).astype("<i2")
    with wave.open(path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(pcm.tobytes())


def main():
    os.makedirs(OUT, exist_ok=True)
    catalog = []
    with tempfile.TemporaryDirectory() as tmp:
        for i, (mood, title, artist, key) in enumerate(TRACKS):
            tid = f"{mood.lower()}-{i % 3}"
            x = compose(mood, artist, key, seed=1000 + i)
            wav = os.path.join(tmp, tid + ".wav")
            write_wav(wav, x)
            m4a = os.path.join(OUT, tid + ".m4a")
            subprocess.run(
                ["ffmpeg", "-y", "-loglevel", "error", "-i", wav, "-c:a", "aac", "-b:a", "48k", "-movflags", "+faststart", m4a],
                check=True,
            )
            catalog.append({
                "id": tid,
                "file": f"music/{tid}.m4a",
                "title": title,
                "artist": artist,
                "genre": ARTISTS[artist]["genre"],
                "durationSec": SECONDS,
                "intent": mood,
            })
            print(f"{tid:10s} {title} · {artist} ({os.path.getsize(m4a) // 1024} KB)", flush=True)
    with open(os.path.join(OUT, "tracks.json"), "w", encoding="utf-8") as f:
        json.dump({"version": 1, "note": "tools/music-gen 으로 만든 HAIDA 데모 곡. 외부 음원 없음.", "tracks": catalog}, f, ensure_ascii=False, indent=2)


if __name__ == "__main__":
    sys.exit(main())
