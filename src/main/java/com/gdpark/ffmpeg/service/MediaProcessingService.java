package com.gdpark.ffmpeg.service;

import net.bramp.ffmpeg.FFmpeg;
import net.bramp.ffmpeg.FFmpegExecutor;
import net.bramp.ffmpeg.FFprobe;
import net.bramp.ffmpeg.builder.FFmpegBuilder;
import net.bramp.ffmpeg.probe.FFmpegProbeResult;

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
 *
 * <p>FFmpeg를 사용하여 프레임 추출, 오디오 추출, 비디오 클립 생성(자르기) 등의 기능을 수행합니다.
 */
@Service
public class MediaProcessingService {

  private final FFmpeg ffmpeg;
  private final FFprobe ffprobe;

  @Value("${ffmpeg.work-dir}")
  private final String workDir;

  /**
   * 필요한 FFmpeg/FFprobe 클라이언트와 작업 디렉토리 경로를 주입받아 MediaProcessingService를 생성합니다.
   *
   * @param ffmpeg FFmpeg 클라이언트 (인코딩 명령어 실행용)
   * @param ffprobe FFprobe 클라이언트 (미디어 메타데이터 조회용)
   * @param workDir 결과 파일(예: 추출된 오디오)이 저장될 파일 시스템 경로
   */
  @Autowired
  public MediaProcessingService(FFmpeg ffmpeg, FFprobe ffprobe, String workDir) {
    this.ffmpeg = ffmpeg;
    this.ffprobe = ffprobe;
    this.workDir = workDir;
  }

  /**
   * 비디오 파일에서 오디오를 추출하여 설정된 작업 디렉토리에 WAV 파일로 저장합니다.
   *
   * @param inputPath 원본 비디오 파일 경로
   * @return 추출된 WAV 오디오 파일의 파일 시스템 경로
   * @throws IOException 작업 디렉토리 준비 실패 또는 파일 쓰기 오류 발생 시
   */
  public String extractAudio(String inputPath) throws IOException {
    String fileName = "audio_" + System.currentTimeMillis() + ".wav";
    Path outputPath = Paths.get(workDir, fileName);

    // 작업 디렉토리 생성 확인
    Files.createDirectories(Paths.get(workDir));

    FFmpegProbeResult probeResult = ffprobe.probe(inputPath);

    FFmpegBuilder builder =
        new FFmpegBuilder()
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
   * FFmpegExecutor를 사용하여 제공된 FFmpegBuilder 작업을 실행합니다.
   *
   * @param builder 실행할 FFmpeg 작업 내용을 담은 빌더 객체
   */
  private void run(FFmpegBuilder builder) {
    FFmpegExecutor executor = new FFmpegExecutor(ffmpeg, ffprobe);
    executor.createJob(builder).run();
  }
}
