package com.gdpark.ffmpeg.service;

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
     * @return 감지된 장면 정보 리스트
     */
    public List<SceneResult> detectScenes(String inputPath, double threshold) throws IOException {
        log.info("장면 감지 분석 시작: Input={}, Threshold={}", inputPath, threshold);

        Path outputBaseDir = Paths.get(workDir, "scenes_" + System.currentTimeMillis());
        Files.createDirectories(outputBaseDir);

        // 장면 전환 타임스탬프 감지
        List<Double> sceneTimes = detectSceneChanges(inputPath, threshold);

        // 타임스탬프를 기반으로 장면 구간(Start~End) 정의
        List<SceneSegment> segments = createSegments(sceneTimes, inputPath);

        // 각 구간별 클립 및 썸네일 생성
        List<SceneResult> results = new ArrayList<>();
        for (int i = 0; i < segments.size(); i++) {
            SceneSegment segment = segments.get(i);

            // 너무 짧은 구간(0.5초 미만)은 스킵 (노이즈 방지)
            if (segment.duration() < 0.5) {
                continue;
            }

            String clipName = String.format("scene_%03d.mp4", i + 1);
            String thumbName = String.format("thumb_%03d.jpg", i + 1);
            Path clipPath = outputBaseDir.resolve(clipName);
            Path thumbPath = outputBaseDir.resolve(thumbName);

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
        }

        log.info("장면 감지 및 처리 완료: {} scenes detected.", results.size());
        return results;
    }

    /**
     * 영상 내 장면 전환(Scene Change) 타임스탬프를 감지합니다.
     * <p>
     * 참고: `net.bramp.ffmpeg` 라이브러리는 필터 실행 결과(stderr) 파싱을 직접 지원하지 않아,
     * `ProcessBuilder`를 사용하여 직접 `ffprobe` 명령어를 실행하고 출력을 파싱하는 방식으로 구현했습니다.
     * </p>
     *
     * @param inputPath 입력 파일 경로
     * @param threshold 장면 감지 임계값
     * @return 장면 전환이 감지된 시간(초) 리스트
     */
    private List<Double> detectSceneChanges(String inputPath, double threshold) throws IOException {
        List<Double> timestamps = new ArrayList<>();
        timestamps.add(0.0); // 시작점

        // 실행 명령어: ffprobe -v quiet -show_entries frame=pkt_pts_time -of
        // default=noprint_wrappers=1:nokey=1 -f lavfi -i
        // "movie=input.mp4,select=gt(scene,threshold)"
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "ffprobe",
                    "-v", "quiet",
                    "-show_entries", "frame=pkt_pts_time",
                    "-of", "default=noprint_wrappers=1:nokey=1",
                    "-f", "lavfi",
                    "-i", String.format("movie=%s,select=gt(scene\\,%f)", inputPath, threshold));

            Process process = pb.start();
            try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    try {
                        double t = Double.parseDouble(line.trim());
                        timestamps.add(t);
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
            process.waitFor();

        } catch (Exception e) {
            log.error("장면 감지 실패 (ffprobe)", e);
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

        if (timestamps.isEmpty()) {
            timestamps.add(0.0);
        }

        for (int i = 0; i < timestamps.size(); i++) {
            double start = timestamps.get(i);
            double end;

            if (i < timestamps.size() - 1) {
                end = timestamps.get(i + 1);
            } else {
                end = totalDuration > start ? totalDuration : start + 10.0;
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