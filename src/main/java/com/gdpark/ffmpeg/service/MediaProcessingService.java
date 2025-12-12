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
 * <p>FFmpeg를 사용하여 프레임 추출, 오디오 추출, 비디오 클립 생성(자르기) 등의 기능을 수행합니다.
 */
@Service
public class MediaProcessingService {

  private final FFmpeg ffmpeg;
  private final FFprobe ffprobe;

  @Value("${ffmpeg.work-dir}")
  private final String workDir;

  /**
   * Create a MediaProcessingService configured with the FFmpeg binary, FFprobe binary, and a working directory for outputs.
   *
   * @param ffmpeg  FFmpeg client used to execute media processing jobs
   * @param ffprobe FFprobe client used to inspect media files and obtain metadata
   * @param workDir filesystem path to the directory where output files will be created (may be relative or absolute)
   */
  @Autowired
  public MediaProcessingService(FFmpeg ffmpeg, FFprobe ffprobe, String workDir) {
    this.ffmpeg = ffmpeg;
    this.ffprobe = ffprobe;
    this.workDir = workDir;
  }

  /**
   * Extracts the audio track from the specified video file and saves it as a WAV file in the configured work directory.
   *
   * @param inputPath the path to the source video file
   * @return the filesystem path of the extracted WAV file
   * @throws IOException if directory creation, probing, or FFmpeg execution fails
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
   * Executes the provided FFmpegBuilder as an FFmpeg job using this service's FFmpeg and FFprobe instances.
   *
   * @param builder an FFmpegBuilder configured for the job to run
   */
  private void run(FFmpegBuilder builder) {
    FFmpegExecutor executor = new FFmpegExecutor(ffmpeg, ffprobe);
    executor.createJob(builder).run();
  }
}