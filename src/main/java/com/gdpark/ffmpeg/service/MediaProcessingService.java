package com.gdpark.ffmpeg.service;

import net.bramp.ffmpeg.FFmpeg;
import net.bramp.ffmpeg.FFmpegExecutor;
import net.bramp.ffmpeg.FFprobe;
import net.bramp.ffmpeg.builder.FFmpegBuilder;
import net.bramp.ffmpeg.probe.FFmpegProbeResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

/**
 * 미디어 처리(가공)를 담당하는 서비스입니다.
 * <p>
 * FFmpeg를 사용하여 프레임 추출, 오디오 추출, 비디오 클립 생성(자르기) 등의 기능을 수행합니다.
 * </p>
 */
@Service
public class MediaProcessingService {

    private static final Logger log = LoggerFactory.getLogger(MediaProcessingService.class);

    private final FFmpeg ffmpeg;
    private final FFprobe ffprobe;
    private final String workDir;

    @Autowired
    public MediaProcessingService(FFmpeg ffmpeg, FFprobe ffprobe, @Value("${ffmpeg.work-dir}") String workDir) {
        this.ffmpeg = ffmpeg;
        this.ffprobe = ffprobe;
        this.workDir = workDir;
    }

    /**
     * 영상에서 프레임을 추출하여 이미지로 저장합니다.
     *
     * @param inputPath 입력 영상 파일 경로
     * @param fps       초당 추출할 프레임 수 (FPS)
     * @return 프레임 이미지가 저장된 디렉토리 경로
     */
    public String extractFrames(String inputPath, double fps) throws IOException {
        log.info("프레임 추출 시작: Input={}, FPS={}", inputPath, fps);

        String outputDirName = "frames_" + System.currentTimeMillis();
        Path outputDir = Paths.get(workDir, outputDirName);
        Files.createDirectories(outputDir);

        String outputPathPattern = outputDir.resolve("frame_%04d.jpg").toString();

        FFmpegProbeResult probeResult = ffprobe.probe(inputPath);

        FFmpegBuilder builder = new FFmpegBuilder()
                .setInput(probeResult)
                .overrideOutputFiles(true)
                .addOutput(outputPathPattern)
                .setFormat("image2")
                .setVideoFilter("fps=" + fps)
                .done();

        run(builder);

        log.info("프레임 추출 완료: OutputDir={}", outputDir);
        return outputDir.toString();
    }

    /**
     * 영상에서 오디오를 추출하여 WAV 파일로 저장합니다.
     *
     * @param inputPath 입력 영상 파일 경로
     * @return 추출된 오디오 파일 경로
     */
    public String extractAudio(String inputPath) throws IOException {
        String fileName = "audio_" + System.currentTimeMillis() + ".wav";
        Path outputPath = Paths.get(workDir, fileName);

        // 작업 디렉토리 생성 확인
        Files.createDirectories(Paths.get(workDir));

        FFmpegProbeResult probeResult = ffprobe.probe(inputPath);

        FFmpegBuilder builder = new FFmpegBuilder()
                .setInput(probeResult)
                .overrideOutputFiles(true)
                .addOutput(outputPath.toString())
                .disableVideo()
                .setAudioCodec("pcm_s16le") // wav 표준 코덱
                .setAudioSampleRate(44100)
                .setAudioChannels(2)
                .done();

        run(builder);

        return outputPath.toString();
    }

    /**
     * 영상의 특정 구간을 잘라내어 새로운 파일로 저장합니다.
     *
     * @param inputPath         입력 영상 파일 경로
     * @param startTime         시작 시간 (예: "00:00:30" 또는 "30")
     * @param durationOrEndTime 길이 또는 종료 시간 (현재 로직은 길이(duration)로 해석)
     * @return 생성된 클립 파일 경로
     */
    public String createClip(String inputPath, String startTime, String durationOrEndTime) throws IOException {
        String fileName = "clip_" + System.currentTimeMillis() + ".mp4";
        Path outputPath = Paths.get(workDir, fileName);
        Files.createDirectories(Paths.get(workDir));

        FFmpegProbeResult probeResult = ffprobe.probe(inputPath);

        // 시간 파싱
        long startMs = parseTimeToMillis(startTime);
        long durationMs = parseTimeToMillis(durationOrEndTime);

        FFmpegBuilder builder = new FFmpegBuilder()
                .setInput(probeResult)
                .overrideOutputFiles(true)
                .addOutput(outputPath.toString())
                .setStartOffset(startMs, TimeUnit.MILLISECONDS)
                .setDuration(durationMs, TimeUnit.MILLISECONDS)
                .setVideoCodec("copy") // 속도를 위해 재인코딩 없이 복사
                .setAudioCodec("copy")
                .done();

        run(builder);

        return outputPath.toString();
    }

    private void run(FFmpegBuilder builder) {
        FFmpegExecutor executor = new FFmpegExecutor(ffmpeg, ffprobe);
        executor.createJob(builder).run();
    }

    /**
     * 시간 문자열을 밀리초(ms) 단위로 변환합니다.
     *
     * @param timeStr 시간 문자열 (HH:mm:ss 또는 초 단위 숫자)
     * @return 밀리초 단위 시간
     */
    private long parseTimeToMillis(String timeStr) {
        try {
            String[] parts = timeStr.split(":");
            if (parts.length == 3) {
                long h = Long.parseLong(parts[0]);
                long m = Long.parseLong(parts[1]);
                long s = Long.parseLong(parts[2]);
                return (h * 3600 + m * 60 + s) * 1000;
            } else {
                return Long.parseLong(timeStr) * 1000;
            }
        } catch (NumberFormatException e) {
            log.warn("시간 파싱 실패, 기본값 0 사용: {}", timeStr);
            return 0;
        }
    }
}
