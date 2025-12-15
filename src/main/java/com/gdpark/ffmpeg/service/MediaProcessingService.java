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

/**
 * 미디어 처리(가공)를 담당하는 서비스입니다.
 *
 * <p>
 * FFmpeg를 사용하여 프레임 추출, 오디오 추출, 비디오 클립 생성(자르기) 등의 기능을 수행합니다.
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
   * 영상에서 오디오를 추출하여 WAV 파일로 저장합니다.
   *
   * @param inputPath 입력 영상 파일 경로
   * @return 추출된 오디오 파일 경로
   */
  public String extractAudio(String inputPath) throws IOException {
    return extractAudio(inputPath, 16000);
  }

  /**
   * 영상에서 오디오를 추출하여 WAV 파일로 저장합니다.
   *
   * @param inputPath  입력 영상 파일 경로
   * @param sampleRate 오디오 샘플 레이트 (예: 16000, 44100)
   * @return 추출된 오디오 파일 경로
   */
  public String extractAudio(String inputPath, int sampleRate) throws IOException {
    String fileName = "audio_" + sampleRate + "_" + System.currentTimeMillis() + ".mp3";
    Path outputPath = Paths.get(workDir, fileName);

    // 작업 디렉토리 생성 확인
    Files.createDirectories(Paths.get(workDir));

    FFmpegProbeResult probeResult = ffprobe.probe(inputPath);

    FFmpegBuilder builder = new FFmpegBuilder()
        .setInput(probeResult)
        .overrideOutputFiles(true)
        .addOutput(outputPath.toString())
        .disableVideo()
        .setAudioCodec("libmp3lame") // wav 표준 코덱
        .setAudioSampleRate(sampleRate)
        .setAudioBitRate(128_000)
        .setAudioChannels(1) // STT용은 보통 Mono 권장
        .done();

    run(builder);

    return outputPath.toString();
  }

  /**
   * FFmpegExecutor를 사용하여 제공된 FFmpegBuilder 작업을 실행합니다.
   *
   * @param builder 실행할 FFmpeg 작업 내용을 담은 빌더 객체
   */
  private void run(FFmpegBuilder builder) {
    FFmpegExecutor executor = new FFmpegExecutor(ffmpeg, ffprobe);
    executor.createJob(builder).run();
  }
}
