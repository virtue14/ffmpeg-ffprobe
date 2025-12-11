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
brew update && brew install ffmpeg
```

> Homebrew의 `ffmpeg` 패키지에는 보통 `ffprobe`가 함께 포함됨.

설치 확인:

```bash
ffmpeg -version && ffprobe -version
```

```bash
which ffmpeg && which ffprobe
```

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
ffprobe -v quiet   -print_format json   -show_format   -show_streams   ./samples/input.mp4
```

### 1fps frame extraction

```bash
mkdir -p out/frames_1fps
ffmpeg -i ./samples/input.mp4   -vf "fps=1"   out/frames_1fps/frame_%04d.jpg
```

### 특정 구간에서 2fps 추출

```bash
mkdir -p out/frames_segment
ffmpeg -ss 00:00:10 -to 00:00:20   -i ./samples/input.mp4   -vf "fps=2"   out/frames_segment/frame_%04d.jpg
```

### 키프레임만 추출

```bash
mkdir -p out/keyframes
ffmpeg -i ./samples/input.mp4   -vf "select=eq(pict_type\,I)"   -vsync vfr   out/keyframes/key_%04d.jpg
```

### audio to wav

```bash
mkdir -p out/audio
ffmpeg -i ./samples/input.mp4   -vn -ar 44100 -ac 2   out/audio/audio.wav
```

### clip export (no re-encode)

```bash
mkdir -p out/clips
ffmpeg -ss 00:00:30 -to 00:00:45   -i ./samples/input.mp4   -c copy   out/clips/clip_30_45.mp4
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
