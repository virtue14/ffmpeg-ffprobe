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
   * Constructs a MediaProcessingService with the required FFmpeg/FFprobe clients and working directory.
   *
   * @param ffmpeg  FFmpeg client used to execute encoding commands
   * @param ffprobe FFprobe client used to probe media files for metadata
   * @param workDir filesystem path where output files (e.g., extracted audio) will be written
   */
  @Autowired
  public MediaProcessingService(FFmpeg ffmpeg, FFprobe ffprobe, String workDir) {
    this.ffmpeg = ffmpeg;
    this.ffprobe = ffprobe;
    this.workDir = workDir;
  }

  /**
   * Extracts audio from the given video file and saves it as a WAV file in the configured work directory.
   *
   * @param inputPath path to the source video file
   * @return the filesystem path of the extracted WAV audio file
   * @throws IOException if an I/O error occurs while preparing the work directory or writing the output file
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
   * Executes the provided FFmpegBuilder using an FFmpegExecutor.
   *
   * @param builder the FFmpegBuilder describing the FFmpeg job to run
   */
  private void run(FFmpegBuilder builder) {
    FFmpegExecutor executor = new FFmpegExecutor(ffmpeg, ffprobe);
    executor.createJob(builder).run();
  }
}