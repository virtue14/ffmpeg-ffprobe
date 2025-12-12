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

  @Autowired
  public MediaInfoService(FFprobe ffprobe) {
    this.ffprobe = ffprobe;
  }

  /**
   * 미디어 파일의 상세 메타데이터를 조회합니다.
   *
   * @param inputPath 조회할 미디어 파일의 절대 경로
   * @return FFprobe 실행 결과 (비디오/오디오 스트림 정보 포함)
   * @throws IOException FFprobe 실행 실패 시 발생
   */
  public FFmpegProbeResult getMetadata(String inputPath) throws IOException {
    // FFprobe를 사용하여 미디어 파일 정보를 조회
    return ffprobe.probe(inputPath);
  }
}
