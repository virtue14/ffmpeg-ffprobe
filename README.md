# ffmpeg-ffprobe (Spring Boot)

FFmpeg + FFprobe를 **Spring Boot(Java)에서 프로세스로 호출**하여  
영상/오디오/프레임 추출 및 메타데이터 분석 기능을 제공하는 프로젝트.

- Language: **Java**
- Build: **Gradle (Kotlin DSL)**
- OS: **macOS (Apple Silicon, M1)**

---

## Goals

- FFprobe로 파일 특성/스트림 메타데이터를 JSON 형태로 조회
- FFmpeg로 프레임/오디오/구간 클립 등 미디어 리소스 추출
- Spring Boot 프로젝트에서 재사용 가능한 구조/패턴 제공

---

## Environment

- macOS (Apple Silicon)
- Tested on: **MacBook Pro M1**
- JDK 17+
- Spring Boot 3.x
- Shell: zsh / bash

---

## Local Install (macOS)

### Homebrew

```bash

brew update && brew install ffmpeg```

> Homebrew의 `ffmpeg` 패키지에는 보통 `ffprobe`가 함께 포함됨.

설치 확인:

```bash

ffmpeg -version && ffprobe -version```

```bash

which ffmpeg && which ffprobe```

---

## Configuration

`application.yml` 예시:

```yaml
ffmpeg:
  ffmpeg-path: /opt/homebrew/bin/ffmpeg
  ffprobe-path: /opt/homebrew/bin/ffprobe
  work-dir: ./out
```

> 위 경로는 M1 Homebrew 기본 경로 기준.  
> `which ffmpeg`, `which ffprobe` 결과에 맞춰 수정하면 됨.

---

## Suggested Project Structure

```plaintext
ffmpeg-ffprobe/
├── README.md
├── build.gradle.kts
├── src/main/java
│   └── com/gdpark/ffmpeg
│       ├── FfmpegApplication.java
│       ├── config
│       │   └── FfmpegProperties.java
│       ├── controller
│       │   └── MediaController.java
│       ├── service
│       │   ├── FfmpegService.java
│       │   └── FfprobeService.java
│       └── util
│           └── ProcessRunner.java
└── samples/
    └── input.mp4
```

---

## Gradle (Kotlin DSL)

`build.gradle.kts` 예시:

```kotlin
plugins {
    id("java")
    id("org.springframework.boot") version "3.3.2"
    id("io.spring.dependency-management") version "1.1.6"
}

group = "com.gdpark"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")

    // JSON 응답/요청 처리
    implementation("com.fasterxml.jackson.core:jackson-databind")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
```

---

## API Examples (suggested)

> 아래는 구현 방향을 보여주는 예시 엔드포인트.  
> 실제 구현 시 요청/응답 DTO와 예외 처리를 추가하는 것을 권장.

### 1) 메타데이터 조회

```http
GET /media/metadata?path=./samples/input.mp4
```

### 2) 프레임 추출 (1fps 등)

```http
POST /media/frames
Content-Type: application/json

{
  "path": "./samples/input.mp4",
  "fps": 1
}
```

### 3) 오디오 추출 (wav)

```http
POST /media/audio
Content-Type: application/json

{
  "path": "./samples/input.mp4"
}
```

### 4) 구간 클립 생성

```http
POST /media/clip
Content-Type: application/json

{
  "path": "./samples/input.mp4",
  "start": "00:00:30",
  "end": "00:00:45"
}
```

---

## Core Idea

Spring Boot에서는 FFmpeg/FFprobe를  
**OS 프로세스 실행 방식으로 사용하는 것이 가장 단순하고 실용적임**.

- 장점
    - 최신 ffmpeg 기능/옵션을 그대로 활용
    - 특정 포맷/코덱 대응이 유연
    - 디버깅/재현이 쉬움
- 주의
    - 바이너리 경로 설정
    - 실행 시간 긴 작업은 비동기 처리 고려
    - 사용자 입력 경로 검증(보안)

---

## Sample Commands (for debugging)

### FFprobe JSON

```bash
ffprobe -v quiet -print_format json -show_format -show_streams ./samples/input.mp4
```

### 1fps frame extraction
```bash
mkdir -p out/frames_1fps &&  ffmpeg -i ./samples/input.mp4 -vf "fps=1" out/frames_1fps/frame_%04d.jpg
```

### 특정 구간에서 2fps 추출
```bash
mkdir -p out/frames_segment && ffmpeg -ss 00:00:10 -to 00:00:20 -i ./samples/input.mp4 -vf "fps=2" out/frames_segment/frame_%04d.jpg
```

### 키프레임만 추출
```bash
mkdir -p out/keyframes && ffmpeg -i ./samples/input.mp4 -vf "select=eq(pict_type\,I)" -vsync vfr out/keyframes/key_%04d.jpg
```

### audio to wav
```bash
mkdir -p out/audio && ffmpeg -i ./samples/input.mp4 -vn -ar 44100 -ac 2 out/audio/audio.wav
```

### clip export (no re-encode)
```bash
mkdir -p out/clips && ffmpeg -ss 00:00:30 -to 00:00:45 -i ./samples/input.mp4 -c copy out/clips/clip_30_45.mp4
```

---

## Roadmap

- [ ] ProcessRunner 공통화 및 표준 로깅
- [ ] 작업 상태/진행률 추적(비동기)
- [ ] 파일 업로드 기반 API 확장
- [ ] Docker 지원(멀티플랫폼)
- [ ] Scene 단위 자동 분할(키프레임 기반)

---

## License

MIT


---

## 추가 요구사항 / 확장 아이디어

### 1. 텍스트 추출

이 프로젝트에서는 ffmpeg/ffprobe를 활용해 다음과 같은 형태의 텍스트 추출을 고려할 수 있다.

1. **내장 자막(soft subtitle) 추출**  
   - 영상 컨테이너(mp4/mkv 등)에 자막 트랙이 포함된 경우, ffmpeg로 SRT 등으로 추출 가능
   - 예시:
        ```bash
        ffmpeg -i input.mp4 -map 0:s:0 subs.srt
        ```
   - Spring Boot에서는 `FfprobeService`로 subtitle 스트림 존재 여부를 확인하고, `FfmpegService`에서 위와 같은 명령을 실행하는 식으로 구성할 수 있다.

2. **화면 내 텍스트(OCR) 추출**
    - 영상 안에 글자가 박혀 있는 경우(hardsub, 화면 텍스트)는 ffmpeg만으로는 인식이 불가능하고,
        1) ffmpeg로 프레임 추출
        2) 추출된 이미지에 OCR 엔진(Tesseract, Vision API 등) 적용
    - ffmpeg는 "이미지 추출기" 역할, 텍스트 인식은 별도의 AI/OCR 서비스에서 담당한다.

3. **음성 → 텍스트(STT) 추출**
   - ffmpeg로 음성 트랙을 추출한 뒤, STT(Speech-to-Text) 엔진에 전달하는 형태
   - 예시:
        ```bash
        ffmpeg -i input.mp4 -vn -ar 16000 -ac 1 audio.wav
        ```
     - Spring Boot에서는 `FfmpegService`로 `audio.wav`를 생성하고, `SttService`(외부 API 연동)를 통해 텍스트를 받는 구조로 확장할 수 있다.

---

### 2. 장면 분할 기준 (Scene Detection)

장면 전환이 짧게 변화하는 영상에서는, 단순 프레임 차이만으로 장면을 자르면 너무 많은 "쓸모없는 장면"이 생길 수 있다. 따라서 다음과 같은 기준을 조합해 사용하는 것을 권장한다.

1. **ffmpeg scene 필터 사용**
   - 프레임 간 변화량을 기반으로 씬 전환 후보를 찾는다.
   - 예시:
        ```bash
        ffmpeg -i input.mp4 -vf "select='gt(scene,0.3)',showinfo" -vsync vfr out/scene_%04d.jpg
        ```
     - `scene` threshold(예: 0.3~0.5)를 조절해 얼마나 민감하게 컷을 잡을지 설정한다.

2. **최소 장면 길이(min duration) 설정**
    - 감지된 장면 boundary 사이의 시간이 너무 짧으면 "유효한 장면"으로 보지 않는다.
    - 예: 2~3초 미만의 구간은 제거하거나 이웃 장면과 병합.

3. **가까운 컷 병합**
    - 컷 사이 간격이 일정 시간(예: 1초)보다 짧으면 같은 장면으로 묶는다.
    - 이를 통해 번쩍이는 효과나 아주 짧은 컷이 남발되는 것을 방지할 수 있다.

Spring Boot 서비스 레벨에서는:

- `SceneDetectionService`에서
    - ffmpeg/ffprobe 호출 → 후보 timestamp 목록 획득
    - 최소 길이/병합 규칙을 적용해 최종 장면 리스트를 계산
- 컨트롤러에서는 이 리스트를 클라이언트에 JSON으로 반환하는 식의 구성을 할 수 있다.

---

### 3. 장면 단위 썸네일 생성

잘라낸 각 장면(shot)에 대해 대표 썸네일을 생성할 수 있다. 대표적인 전략은 다음과 같다.

1. **장면 중간 시점 한 프레임 캡처**
    - 장면 시작/끝 시간이 주어졌다고 가정하면, 중간 지점을 썸네일로 사용:
        ```bash
        # start=10, end=20일 때 mid=15초
        ffmpeg -ss 00:00:15 -i input.mp4 -vframes 1 -q:v 2 thumb.jpg
        ```
      - `-q:v`는 화질 옵션으로, 값이 작을수록 고화질(예: 2~4 추천).

2. **`thumbnail` 필터 사용**
   - 해당 구간에서 여러 프레임을 보고 "대표적인" 프레임을 선택:
        ```bash
        ffmpeg -ss 00:00:10 -to 00:00:20 -i input.mp4 -vf "thumbnail,scale=320:-1" -frames:v 1 thumb.jpg
        ```
        - 썸네일 크기(scale)를 조정해 웹/모바일에서 쓰기 좋은 정해진 사이즈로 맞출 수 있다.

3. **프로젝트 내 역할 정리**
    - `SceneDetectionService`
        - 장면별 `{index, start, end}` 리스트 계산
    - `ThumbnailService` (or `FfmpegService` 내부 기능)
        - 각 장면에 대해 중간 지점을 계산하고 ffmpeg로 썸네일 생성
        - 파일명 규칙 예: `thumb_scene_{index}.jpg`

이 요구사항들은 모두 현재 프로젝트 구조(Spring Boot + ffmpeg/ffprobe 프로세스 호출 방식)에 자연스럽게 녹일 수 있으며, 추후 AI(OCR/STT/LLM)와 결합해 영상 요약/검색 서비스로 확장하는 기반이 될 수 있다.
