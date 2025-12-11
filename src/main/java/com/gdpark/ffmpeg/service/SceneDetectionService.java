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
 * <p>
 * 영상의 장면 전환을 감지하여 씬별 비디오 클립과 썸네일을 생성합니다.
 * </p>
 */
@Service
public class SceneDetectionService {

    private static final Logger log = LoggerFactory.getLogger(SceneDetectionService.class);

    private final FFmpeg ffmpeg;
    private final FFprobe ffprobe;
    private final String workDir;

    @Autowired
    public SceneDetectionService(FFmpeg ffmpeg, FFprobe ffprobe, @Value("${ffmpeg.work-dir}") String workDir) {
        this.ffmpeg = ffmpeg;
        this.ffprobe = ffprobe;
        this.workDir = workDir;
    }

    /**
     * 장면(Scene)을 감지하고 각 장면별 비디오 클립과 대표 썸네일을 생성합니다.
     *
     * @param inputPath 입력 비디오 파일 경로
     * @param threshold 장면 감지 임계값 (0.0 ~ 1.0)
     * @return 감지된 장면 정보 응답 객체 (총 개수 및 리스트 포함)
     */
    public SceneDetectionResponse detectScenes(String inputPath, double threshold) throws IOException {
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
                log.debug("구간 스킵 (너무 짧음): {}s ({} ~ {})", String.format("%.2f", segment.duration()), segment.start,
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

                results.add(new SceneResult(
                        segment.start(),
                        segment.end(),
                        clipPath.toAbsolutePath().toString(),
                        thumbPath.toAbsolutePath().toString()));
            } catch (Exception e) {
                log.error("장면 처리 중 오류 발생 (Index: {}): {}", sceneIndex, e.getMessage());
            }
        }

        log.info("장면 감지 및 처리 완료: Total Scenes={}", results.size());
        return new SceneDetectionResponse(results.size(), results);
    }

    /**
     * 영상 내 장면 전환(Scene Change) 타임스탬프를 감지합니다.
     * <p>
     * 1. `pkt_pts_time` 대신 `pts_time`을 사용하여 lavfi 필터 출력 호환성 개선.
     * 2. 1차 시도 실패(장면 감지 0개) 시, 임계값을 낮춰(Threshold * 0.5) 재시도하는 Adaptive Logic 적용.
     * 3. 재시도 실패 시 10분 단위로 강제 분할하지는 않지만, 로그를 남김.
     * </p>
     *
     * @param inputPath 입력 파일 경로
     * @param threshold 장면 감지 임계값
     * @return 장면 전환이 감지된 시간(초) 리스트
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

    private List<Double> runFfprobeForSceneDetection(String inputPath, double threshold) {
        List<Double> timestamps = new ArrayList<>();
        timestamps.add(0.0); // 시작점

        // pkt_pts_time -> pts_time으로 변경 (lavfi 출력 호환성)
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "ffprobe",
                    "-v", "error",
                    "-show_entries", "frame=pts_time",
                    "-of", "default=noprint_wrappers=1:nokey=1",
                    "-f", "lavfi",
                    "-i", String.format("movie=%s,select=gt(scene\\,%f)", inputPath, threshold));

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
     * 특정 구간의 영상을 잘라내어 저장합니다.
     *
     * @param inputPath  원본 영상 경로
     * @param start      시작 시간 (초)
     * @param duration   길이 (초)
     * @param outputPath 저장할 파일 경로
     */
    private void createClip(String inputPath, double start, double duration, String outputPath) throws IOException {
        FFmpegBuilder builder = new FFmpegBuilder()
                .setInput(inputPath)
                .overrideOutputFiles(true)
                .addOutput(outputPath)
                .setStartOffset((long) (start * 1000), java.util.concurrent.TimeUnit.MILLISECONDS)
                .setDuration((long) (duration * 1000), java.util.concurrent.TimeUnit.MILLISECONDS)
                .setVideoCodec("libx264")
                .setAudioCodec("aac")
                .setStrict(FFmpegBuilder.Strict.EXPERIMENTAL)
                .done();

        new FFmpegExecutor(ffmpeg, ffprobe).createJob(builder).run();
    }

    /**
     * 특정 시점의 프레임을 추출하여 이미지로 저장합니다.
     *
     * @param inputPath  원본 영상 경로
     * @param time       추출 시점 (초)
     * @param outputPath 저장할 이미지 경로
     */
    private void extractThumbnail(String inputPath, double time, String outputPath) throws IOException {
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
     * 감지된 타임스탬프 목록을 바탕으로 시작/종료 구간(SceneSegment)을 생성합니다.
     *
     * @param timestamps 장면 전환 타임스탬프 리스트
     * @param inputPath  영상 전체 길이를 확인하기 위한 파일 경로
     * @return 구간 정보 리스트
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

    /**
     * 내부 사용용 구간 정보 레코드
     */
    private record SceneSegment(double start, double end) {
        public double duration() {
            return end - start;
        }
    }
}