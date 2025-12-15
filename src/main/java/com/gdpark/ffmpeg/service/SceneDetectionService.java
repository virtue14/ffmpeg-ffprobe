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
 * <p>영상의 장면 전환을 감지하여 씬별 비디오 클립과 썸네일을 생성합니다.
 */
@Service
public class SceneDetectionService {

  private static final Logger log = LoggerFactory.getLogger(SceneDetectionService.class);

  private final FFmpeg ffmpeg;
  private final FFprobe ffprobe;
  private final String workDir;

  /**
   * Creates a SceneDetectionService configured with the FFmpeg and FFprobe clients and a working directory.
   *
   * @param ffmpeg  FFmpeg client used to run encoding and clipping operations
   * @param ffprobe FFprobe client used to probe media and detect scene timestamps
   * @param workDir filesystem path used as the base working directory for generated outputs
   */
  @Autowired
  public SceneDetectionService(
      FFmpeg ffmpeg, FFprobe ffprobe, @Value("${ffmpeg.work-dir}") String workDir) {
    this.ffmpeg = ffmpeg;
    this.ffprobe = ffprobe;
    this.workDir = workDir;
  }

  /**
   * Detects scene boundaries in the given video and produces per-scene video clips and thumbnail images.
   *
   * The generated files are written into a timestamped subdirectory of the service's configured work directory.
   * Segments shorter than 0.5 seconds are skipped. If initial detection yields few scenes the method may retry
   * with a lower threshold to improve detection. Failures while processing an individual segment are logged and
   * do not abort processing of other segments.
   *
   * @param inputPath the path to the input video file
   * @param threshold scene detection sensitivity in the range 0.0 to 1.0 (higher values require stronger changes)
   * @return a SceneDetectionResponse containing the total number of produced scenes and a list of SceneResult entries
   * @throws IOException if filesystem access or underlying ffmpeg/ffprobe operations fail
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
   * Detects scene-change timestamps in the given video, using ffprobe and an adaptive threshold retry.
   *
   * <p>If the initial detection yields no scene boundaries (apart from the implicit 0.0 start) and the
   * provided threshold is greater than 0.1, the method retries detection with a reduced threshold
   * (max(0.05, threshold * 0.5)). If detection still fails, a warning is logged and the original
   * timestamp list (typically containing only 0.0) is returned.
   *
   * @param inputPath path to the input video file
   * @param threshold scene-detection sensitivity threshold (higher = stricter)
   * @return list of scene-change timestamps in seconds (includes 0.0 as the video start)
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
   * Detects scene-change timestamps from the input video using ffprobe's lavfi scene filter.
   *
   * <p>Collects frame `pts_time` values (seconds) where the scene score exceeds the given
   * threshold. The returned list reflects detection order and always includes `0.0` as the first
   * element. Non-numeric lines from ffprobe's output are ignored. If an error occurs or no scene
   * changes are detected beyond the start, the list may contain only `0.0`.
   *
   * @param inputPath path to the input video file
   * @param threshold scene detection sensitivity (higher values require a larger visual change to
   *     register a scene)
   * @return a list of scene-change timestamps in seconds; first element is `0.0`
   */
  private List<Double> runFfprobeForSceneDetection(String inputPath, double threshold) {
    List<Double> timestamps = new ArrayList<>();
    timestamps.add(0.0); // 시작점

    // pkt_pts_time -> pts_time으로 변경 (lavfi 출력 호환성)
    try {
      ProcessBuilder pb =
          new ProcessBuilder(
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

      try (var reader =
          new java.io.BufferedReader(new java.io.InputStreamReader(process.getInputStream()))) {
        String line;
        while ((line = reader.readLine()) != null) {
          if (line.isEmpty()) continue;
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
   * Create a video clip from a specified segment of the input video and save it to disk.
   *
   * Uses stream copy for video and audio (no re-encoding), preserving original codecs.
   *
   * @param inputPath  path to the source video file
   * @param start      segment start time in seconds
   * @param duration   segment duration in seconds
   * @param outputPath destination file path for the clipped video
   * @throws IOException if FFmpeg execution or file I/O fails
   */
  private void createClip(String inputPath, double start, double duration, String outputPath)
      throws IOException {
    long startTime = System.currentTimeMillis();

    FFmpegBuilder builder =
        new FFmpegBuilder()
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
   * Extracts a single frame from the video at the specified time and saves it as an image file.
   *
   * @param inputPath  path to the source video
   * @param time       timestamp in seconds where the frame will be extracted
   * @param outputPath destination path for the resulting image
   * @throws IOException if the frame extraction or file writing fails
   */
  private void extractThumbnail(String inputPath, double time, String outputPath)
      throws IOException {
    FFmpegBuilder builder =
        new FFmpegBuilder()
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
   * Converts ordered scene-change timestamps into start/end scene segments.
   *
   * <p>Uses the provided timestamps (in seconds) to produce contiguous SceneSegment entries;
   * the final segment end is set to the video duration obtained from {@code inputPath} when available.
   * If video duration cannot be determined, the final segment end defaults to start + 10.0 seconds.
   * If any computed end is less than or equal to its start, the end is set to start + 5.0 seconds to
   * guarantee a minimum segment length.</p>
   *
   * @param timestamps list of scene-change timestamps in seconds (will be sorted and deduplicated)
   * @param inputPath path to the input video used to probe total duration
   * @return a list of SceneSegment objects representing start/end intervals for each scene
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
     * Get the duration of the scene segment in seconds.
     *
     * @return the segment duration in seconds
     */
    public double duration() {
      return end - start;
    }
  }
}