package com.gdpark.ffmpeg.service;

import net.bramp.ffmpeg.FFprobe;
import net.bramp.ffmpeg.probe.FFmpegProbeResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;

/**
 * 미디어 파일의 메타데이터 정보를 조회하는 서비스입니다.
 *
 * <p>FFprobe를 사용하여 영상/오디오 파일의 상세 스펙(코덱, 길이, 해상도 등)을 추출합니다.
 */
@Service
public class MediaInfoService {

  private final FFprobe ffprobe;

  /**
   * Create a MediaInfoService with the given FFprobe client used to retrieve media metadata.
   *
   * @param ffprobe FFprobe client used to probe media files and obtain metadata (codecs, duration, streams, etc.).
   */
  @Autowired
  public MediaInfoService(FFprobe ffprobe) {
    this.ffprobe = ffprobe;
  }

  /**
   * Retrieve detailed metadata for the media file at the given absolute path.
   *
   * @param inputPath the absolute path to the media file
   * @return the FFprobe execution result containing format and stream (audio/video) information
   * @throws IOException if FFprobe execution fails
   */
  public FFmpegProbeResult getMetadata(String inputPath) throws IOException {
    // FFprobe를 사용하여 미디어 파일 정보를 조회
    return ffprobe.probe(inputPath);
  }
}