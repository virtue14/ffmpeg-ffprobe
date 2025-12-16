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

@Service
public class VideoClipService {

    private static final Logger log = LoggerFactory.getLogger(VideoClipService.class);

    private final FFmpeg ffmpeg;
    private final FFprobe ffprobe;
    private final String workDir;

    @Autowired
    public VideoClipService(FFmpeg ffmpeg, FFprobe ffprobe, @Value("${ffmpeg.work-dir}") String workDir) {
        this.ffmpeg = ffmpeg;
        this.ffprobe = ffprobe;
        this.workDir = workDir;
    }

    /**
     * SRT 형식의 자막 텍스트를 파싱하여 비디오 클립과 썸네일을 생성합니다.
     *
     * @param inputPath  원본 비디오 파일 경로
     * @param srtContent SRT 포맷의 자막 텍스트
     * @return 생성된 결과 리스트
     */
    public List<SceneResult> createClipsFromSrt(String inputPath, String srtContent) {
        List<SrtSegment> segments = parseSrt(srtContent);
        List<SceneResult> results = new ArrayList<>();

        // 결과 저장 디렉토리 생성
        Path outputBaseDir = Paths.get(workDir, "srt_clips_" + System.currentTimeMillis());
        try {
            Files.createDirectories(outputBaseDir);
        } catch (IOException e) {
            log.error("디렉토리 생성 실패: {}", outputBaseDir, e);
            throw new RuntimeException("작업 디렉토리를 생성할 수 없습니다.", e);
        }

        int index = 0;
        for (SrtSegment segment : segments) {
            index++;
            String clipName = String.format("clip_%03d.mp4", index);
            String thumbName = String.format("thumb_%03d.jpg", index);
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
                        thumbPath.toAbsolutePath().toString(),
                        segment.text()));
            } catch (Exception e) {
                log.error("SRT 클립 생성 중 오류 (Index: {}): {}", index, e.getMessage());
            }
        }
        return results;
    }

    /**
     * 입력 비디오의 특정 구간을 잘라내어 비디오 클립을 생성하고 디스크에 저장합니다.
     *
     * @param inputPath  원본 비디오 파일 경로
     * @param start      구간 시작 시간 (초)
     * @param duration   구간 길이 (초)
     * @param outputPath 생성된 클립 비디오의 저장 경로
     * @throws IOException FFmpeg 실행 또는 파일 I/O 실패 시
     */
    public void createClip(String inputPath, double start, double duration, String outputPath) throws IOException {
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
    public void extractThumbnail(String inputPath, double time, String outputPath) throws IOException {
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

    private List<SrtSegment> parseSrt(String srtContent) {
        List<SrtSegment> segments = new ArrayList<>();
        String[] lines = srtContent.replace("\r\n", "\n").split("\n");

        // 상태: 0=번호대기, 1=타임스탬프대기, 2=텍스트대기
        int state = 0;
        double start = 0;
        double end = 0;
        StringBuilder textBuilder = new StringBuilder();

        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) {
                if (state == 2 && textBuilder.length() > 0) {
                    segments.add(new SrtSegment(start, end, textBuilder.toString().trim()));
                    textBuilder.setLength(0);
                    state = 0;
                }
                continue;
            }

            if (state == 0) {
                // 번호 라인 (무시하고 상태 변경)
                if (line.matches("\\d+")) {
                    state = 1;
                }
            } else if (state == 1) {
                // 타임스탬프 라인: 00:00:02,520 --> 00:00:08,300
                if (line.contains("-->")) {
                    String[] parts = line.split("-->");
                    if (parts.length == 2) {
                        try {
                            start = parseTimestamp(parts[0].trim());
                            end = parseTimestamp(parts[1].trim());
                            state = 2;
                        } catch (Exception e) {
                            log.warn("타임스탬프 파싱 실패: {}", line);
                            state = 0; // 리셋
                        }
                    }
                }
            } else if (state == 2) {
                // 텍스트 라인 (여러 줄일 수 있음)
                if (textBuilder.length() > 0) {
                    textBuilder.append(" ");
                }
                textBuilder.append(line);
            }
        }

        // 마지막 세그먼트 처리
        if (state == 2 && textBuilder.length() > 0) {
            segments.add(new SrtSegment(start, end, textBuilder.toString().trim()));
        }

        return segments;
    }

    private double parseTimestamp(String timestamp) {
        // Format: HH:mm:ss,SSS
        timestamp = timestamp.replace(',', '.');
        String[] parts = timestamp.split(":");
        if (parts.length == 3) {
            double hours = Double.parseDouble(parts[0]);
            double minutes = Double.parseDouble(parts[1]);
            double seconds = Double.parseDouble(parts[2]);
            return hours * 3600 + minutes * 60 + seconds;
        }
        throw new IllegalArgumentException("Invalid timestamp format: " + timestamp);
    }

    private record SrtSegment(double start, double end, String text) {
        public double duration() {
            return end - start;
        }
    }
}
