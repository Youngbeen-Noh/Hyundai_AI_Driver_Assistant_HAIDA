# HAIDA 데모 음원 (기능 3)

`generate_tracks.py` 가 시연용 곡 18개(분위기 6종 × 3곡, 각 60초)를 수식으로 직접 만든다. 외부 음원 · 샘플을 쓰지 않아 저작권 문제가 없다(이 저장소의 곡).

```bash
python tools/music-gen/generate_tracks.py   # numpy · ffmpeg(AAC) 필요
```

- 출력: `app/src/main/assets/music/*.m4a` 와 `tracks.json`
- `tracks.json` 의 `intent` 는 작곡할 때 의도한 분위기다. **앱은 이 값을 쓰지 않고**, 앱 안의 분석기(`music/analysis/AudioAnalyzer`)가 소리를 듣고 분위기를 다시 판단한다. intent 는 분석 결과와 비교하는 데만 쓴다.
- 다른 곡(CC 음원 등)을 넣을 때: 같은 형식으로 `tracks.json` 에 추가하고, `WavDirReport` 로 분석 값을 보며 `MoodProfiles` 기준을 다시 맞춘다.

  ```bash
  ffmpeg -i 곡.m4a -ac 1 -ar 22050 폴더/곡.wav
  HAIDA_WAV_DIR=폴더 ./gradlew testDebugUnitTest --tests '*WavDirReport*' -i
  ```
