package com.gdpark.ffmpeg.service;

import com.gdpark.ffmpeg.dto.SceneDetectionResponse;
import com.gdpark.ffmpeg.dto.SceneResult;
import net.bramp.ffmpeg.FFmpeg;
import net.bramp.ffmpeg.FFmpegExecutor;
import net.bramp.ffmpeg.FFprobe;
import net.bramp.ffmpeg.builder.FFmpegBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * 고급 장면 감지(Scene Detection) 및 처리를 담당하는 서비스입니다.
 *
 * <p>
 * 영상의 장면 전환을 감지하여 씬별 비디오 클립과 썸네일을 생성합니다.
 */
@Service
public class SceneDetectionService {

  private static final Logger log = LoggerFactory.getLogger(SceneDetectionService.class);

  private final FFmpeg ffmpeg;
  private final FFprobe ffprobe;
  private final String workDir;

  /**
   * FFmpeg 및 FFprobe 클라이언트와 작업 디렉토리를 설정하여 SceneDetectionService를 생성합니다.
   *
   * @param ffmpeg  인코딩 및 영상 타르기 작업을 수행하는 FFmpeg 클라이언트
   * @param ffprobe 미디어 조사 및 장면 타임스탬프 감지에 사용되는 FFprobe 클라이언트
   * @param workDir 생성된 결과물이 저장될 기본 작업 디렉토리 경로
   */
  @Autowired
  public SceneDetectionService(
      FFmpeg ffmpeg, FFprobe ffprobe, @Value("${ffmpeg.work-dir}") String workDir) {
    this.ffmpeg = ffmpeg;
    this.ffprobe = ffprobe;
    this.workDir = workDir;
  }

  /**
   * 주어진 비디오에서 장면 경계를 감지하고, 장면별 비디오 클립과 썸네일 이미지를 생성합니다.
   *
   * <p>
   * 생성된 파일들은 서비스에 설정된 작업 디렉토리 내의 타임스탬프가 지정된 하위 디렉토리에 저장됩니다.
   * 0.5초 미만의 짧은 구간은 스킵됩니다.
   * 초기 감지 결과가 적을 경우, 감지율을 높이기 위해 더 낮은 임계값으로 재시도할 수 있습니다.
   * 개별 구간 처리 중 실패하더라도 전체 작업은 중단되지 않고 로그를 남긴 후 계속 진행됩니다.
   *
   * @param inputPath 입력 비디오 파일 경로
   * @param threshold 장면 감지 민감도 (0.0 ~ 1.0 범위, 값이 클수록 더 큰 변화가 필요)
   * @return 생성된 총 장면 수와 SceneResult 항목 목록을 포함하는 SceneDetectionResponse
   * @throws IOException 파일 시스템 접근 또는 내부 ffmpeg/ffprobe 작업 실패 시
   */
  public SceneDetectionResponse detectScenes(String inputPath, double threshold)
      throws IOException {
    long startTime = System.currentTimeMillis();
    log.info("장면 감지 분석 시작: Input={}, Threshold={}", inputPath, threshold);

    // 결과 저장 디렉토리 생성
    Path outputBaseDir = Paths.get(workDir, "scenes_" + System.currentTimeMillis());
    Files.createDirectories(outputBaseDir);

    // 장면 전환 타임스탬프 감지 (핵심 로직 개선)
    List<Double> sceneTimes = detectSceneChanges(inputPath, threshold);
    log.info("감지된 타임스탬프 목록: {}", sceneTimes);

    // 타임스탬프를 기반으로 장면 구간(Start~End) 정의
    List<SceneSegment> segments = createSegments(sceneTimes, inputPath);
    log.info("생성된 구간(Segment) 개수: {}", segments.size());

    // 각 구간별 클립 및 썸네일 생성
    List<SceneResult> results = new ArrayList<>();
    int sceneIndex = 0;

    for (SceneSegment segment : segments) {
      // 너무 짧은 구간(0.5초 미만)은 스킵 (노이즈 방지)
      if (segment.duration() < 0.5) {
        log.debug(
            "구간 스킵 (너무 짧음): {}s ({} ~ {})",
            String.format("%.2f", segment.duration()),
            segment.start,
            segment.end);
        continue;
      }

      sceneIndex++;
      String clipName = String.format("scene_%03d.mp4", sceneIndex);
      String thumbName = String.format("thumb_%03d.jpg", sceneIndex);
      Path clipPath = outputBaseDir.resolve(clipName);
      Path thumbPath = outputBaseDir.resolve(thumbName);

      try {
        // 비디오 클립 생성
        createClip(inputPath, segment.start(), segment.duration(), clipPath.toString());

        // 썸네일 생성 (구간의 중간 지점)
        double midPoint = segment.start() + (segment.duration() / 2.0);
        extractThumbnail(inputPath, midPoint, thumbPath.toString());

        results.add(
            new SceneResult(
                segment.start(),
                segment.end(),
                clipPath.toAbsolutePath().toString(),
                thumbPath.toAbsolutePath().toString()));
      } catch (Exception e) {
        log.error("장면 처리 중 오류 발생 (Index: {}): {}", sceneIndex, e.getMessage());
      }
    }

    long endTime = System.currentTimeMillis();
    long totalTimeMs = endTime - startTime;
    log.info(
        "장면 감지 및 처리 완료: Total Scenes={} (총 소요시간: {}ms, 약 {}초)",
        results.size(),
        totalTimeMs,
        String.format("%.1f", totalTimeMs / 1000.0));

    return new SceneDetectionResponse(results.size(), results);
  }

  /**
   * FFprobe와 적응형 임계값 재시도(Adaptive Threshold Retry) 로직을 사용하여 영상 내 장면 전환 타임스탬프를
   * 감지합니다.
   *
   * <p>
   * 초기 감지에서 장면 경계가 감지되지 않고(시작점 0.0 제외), 제공된 임계값이 0.1보다 큰 경우,
   * 더 낮은 임계값(max(0.05, threshold * 0.5))으로 감지를 재시도합니다.
   * 재시도 후에도 실패할 경우 경고를 로그에 남기고 원본 타임스탬프 리스트(보통 0.0만 포함)를 반환합니다.
   *
   * @param inputPath 입력 비디오 파일 경로
   * @param threshold 장면 감지 민감도 임계값 (클수록 더 엄격함)
   * @return 장면 전환 타임스탬프(초) 리스트 (비디오 시작점인 0.0 포함)
   */
  private List<Double> detectSceneChanges(String inputPath, double threshold) throws IOException {
    List<Double> timestamps = runFfprobeForSceneDetection(inputPath, threshold);

    // Adaptive Logic: 감지된 장면이 없고(시작점 제외), 임계값이 0.1보다 큰 경우 -> 임계값을 절반으로 낮춰 재시도
    if (timestamps.size() <= 1 && threshold > 0.1) {
      double newThreshold = Math.max(0.05, threshold * 0.5);
      log.warn("장면 감지 실패 (Threshold={}). 임계값을 {}로 낮춰 재시도합니다.", threshold, newThreshold);
      List<Double> retryTimestamps = runFfprobeForSceneDetection(inputPath, newThreshold);

      if (retryTimestamps.size() > 1) {
        return retryTimestamps;
      }
    }

    // 그래도 감지 안 되면 로그 남기고 종료 (단일 장면으로 처리됨)
    if (timestamps.size() <= 1) {
      log.warn("최종적으로 장면 감지 실패. 전체 영상을 단일 장면으로 처리합니다.");
    }

    return timestamps;
  }

  /**
   * FFprobe의 lavfi scene 필터를 사용하여 입력 비디오에서 장면 전환 타임스탬프를 감지합니다.
   *
   * <p>
   * 장면 점수가 주어진 임계값을 초과하는 프레임의 `pts_time`(초) 값을 수집합니다.
   * 반환된 리스트는 감지 순서를 반영하며 항상 첫 번째 요소로 `0.0`을 포함합니다.
   * FFprobe 출력 중 숫자가 아닌 라인은 무시됩니다. 오류가 발생하거나 시작점 외에 감지된 장면이 없는 경우,
   * 리스트는 `0.0`만 포함할 수 있습니다.
   *
   * @param inputPath 입력 비디오 파일 경로
   * @param threshold 장면 감지 민감도 (값이 클수록 장면으로 등록되기 위해 더 큰 시각적 변화가 필요함)
   * @return 장면 전환 타임스탬프(초) 리스트 (첫 번째 요소는 항상 `0.0`)
   */
  private List<Double> runFfprobeForSceneDetection(String inputPath, double threshold) {
    List<Double> timestamps = new ArrayList<>();
    timestamps.add(0.0); // 시작점

    // pkt_pts_time -> pts_time으로 변경 (lavfi 출력 호환성)
    try {
      ProcessBuilder pb = new ProcessBuilder(
          "ffprobe",
          "-v",
          "error",
          "-show_entries",
          "frame=pts_time",
          "-of",
          "default=noprint_wrappers=1:nokey=1",
          "-f",
          "lavfi",
          "-i",
          String.format("movie=%s,select=gt(scene\\,%f)", inputPath, threshold));

      pb.redirectErrorStream(true);
      Process process = pb.start();

      try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(process.getInputStream()))) {
        String line;
        while ((line = reader.readLine()) != null) {
          if (line.isEmpty())
            continue;
          try {
            double t = Double.parseDouble(line.trim());
            timestamps.add(t);
          } catch (NumberFormatException e) {
            // 로그가 섞일 수 있으므로 무시하거나 디버그 로그
            log.trace("Non-numeric output line from ffprobe: {}", line);
          }
        }
      }
      process.waitFor();
    } catch (Exception e) {
      log.error("장면 감지 중 오류 발생", e);
    }
    return timestamps;
  }

  /**
   * 입력 비디오의 특정 구간을 잘라내어 비디오 클립을 생성하고 디스크에 저장합니다.
   *
   * <p>
   * 비디오와 오디오 모두 스트림 복사(재인코딩 없음)를 사용하여 원본 코덱을 유지하며 처리 속도를 높입니다.
   *
   * @param inputPath  원본 비디오 파일 경로
   * @param start      구간 시작 시간 (초)
   * @param duration   구간 길이 (초)
   * @param outputPath 생성된 클립 비디오의 저장 경로
   * @throws IOException FFmpeg 실행 또는 파일 I/O 실패 시
   */
  private void createClip(String inputPath, double start, double duration, String outputPath)
      throws IOException {
    long startTime = System.currentTimeMillis();

    FFmpegBuilder builder = new FFmpegBuilder()
        .setInput(inputPath)
        .overrideOutputFiles(true)
        .addOutput(outputPath)
        .setStartOffset((long) (start * 1000), java.util.concurrent.TimeUnit.MILLISECONDS)
        .setDuration((long) (duration * 1000), java.util.concurrent.TimeUnit.MILLISECONDS)
        .setVideoCodec("copy") // 재인코딩 없이 스트림 복사
        .setAudioCodec("copy") // 오디오 복사
        .done();

    new FFmpegExecutor(ffmpeg, ffprobe).createJob(builder).run();

    long endTime = System.currentTimeMillis();
    log.debug("클립 생성 완료: {} (소요시간: {}ms)", outputPath, (endTime - startTime));
  }

  /**
   * 비디오의 특정 시점에서 단일 프레임을 추출하여 이미지 파일로 저장합니다.
   *
   * @param inputPath  원본 비디오 경로
   * @param time       프레임을 추출할 시점 (초)
   * @param outputPath 결과 이미지가 저장될 경로
   * @throws IOException 프레임 추출 또는 파일 쓰기 실패 시
   */
  private void extractThumbnail(String inputPath, double time, String outputPath)
      throws IOException {
    FFmpegBuilder builder = new FFmpegBuilder()
        .setInput(inputPath)
        .overrideOutputFiles(true)
        .addOutput(outputPath)
        .setStartOffset((long) (time * 1000), java.util.concurrent.TimeUnit.MILLISECONDS)
        .setFrames(1)
        .setFormat("image2")
        .done();

    new FFmpegExecutor(ffmpeg, ffprobe).createJob(builder).run();
  }

  /**
   * 정렬된 장면 전환 타임스탬프를 시작/종료 구간(SceneSegment)으로 변환합니다.
   *
   * <p>
   * 제공된 타임스탬프(초)를 사용하여 연속적인 SceneSegment 항목을 생성합니다.
   * 마지막 구간의 종료 시간은 가능한 경우 {@code inputPath}에서 얻은 비디오 전체 길이로 설정됩니다.
   * 비디오 길이를 확인할 수 없는 경우, 마지막 구간의 종료 시간은 시작 시간 + 10.0초로 기본 설정됩니다.
   * 계산된 종료 시간이 시작 시간보다 같거나 작을 경우, 최소 구간 길이를 보장하기 위해 종료 시간을 시작 시간 + 5.0초로 설정합니다.
   *
   * @param timestamps 장면 전환 타임스탬프 리스트 (초 단위, 정렬 및 중복 제거됨)
   * @param inputPath  전체 길이를 조사하기 위한 입력 비디오 경로
   * @return 각 장면의 시작/종료 구간을 나타내는 SceneSegment 객체 리스트
   */
  private List<SceneSegment> createSegments(List<Double> timestamps, String inputPath) {
    double totalDuration = 0;
    try {
      totalDuration = ffprobe.probe(inputPath).getFormat().duration;
    } catch (IOException e) {
      log.warn("영상 길이 조회 실패", e);
    }

    List<SceneSegment> segments = new ArrayList<>();

    // 타임스탬프 정렬 및 중복 제거 (안전을 위해)
    timestamps = timestamps.stream().sorted().distinct().toList();

    // 1. 타임스탬프가 시작점(0.0) 하나뿐인 경우 -> 전체를 하나의 장면으로 간주하지 않으려면?
    // 사용자의 의도: 장면 분할이 안 되면 "실패"에 가까움.
    // 하지만 전체가 하나의 씬일 수도 있으므로 일단 진행하되, 너무 길면(예: 1분 이상) 경고를 남길 수도 있음.

    for (int i = 0; i < timestamps.size(); i++) {
      double start = timestamps.get(i);
      double end;

      if (i < timestamps.size() - 1) {
        end = timestamps.get(i + 1);
      } else {
        // 마지막 타임스탬프 ~ 영상 끝
        end = totalDuration > 0 ? totalDuration : start + 10.0; // totalDuration을 못 구했으면 10초로 가정
      }

      // 시작과 끝이 같거나 역전된 경우 방지
      if (end <= start) {
        end = start + 5.0; // 최소 5초 보장
      }

      segments.add(new SceneSegment(start, end));
    }

    return segments;
  }

  /** 내부 사용용 구간 정보 레코드 */
  private record SceneSegment(double start, double end) {
    /**
     * 장면 구간의 길이를 초 단위로 반환합니다.
     *
     * @return 구간 길이 (초)
     */
    public double duration() {
      return end - start;
    }
  }
}
