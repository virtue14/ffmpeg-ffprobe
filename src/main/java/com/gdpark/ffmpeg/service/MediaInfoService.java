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
   * 제공된 FFprobe 인스턴스를 사용하여 미디어 파일을 조사하는 MediaInfoService를 생성합니다.
   *
   * @param ffprobe 미디어 파일에서 메타데이터를 검색하는 데 사용되는 FFprobe 인스턴스
   */
  @Autowired
  public MediaInfoService(FFprobe ffprobe) {
    this.ffprobe = ffprobe;
  }

  /**
   * FFprobe를 사용하여 미디어 파일의 상세 메타데이터를 조회합니다.
   *
   * <p>반환된 조회 결과에는 비디오 및 오디오 스트림 정보뿐만 아니라 포맷 수준의 메타데이터(길이, 비트 전송률, 코덱 등)가 포함됩니다.
   *
   * @param inputPath 조사할 미디어 파일의 절대 경로
   * @return 미디어 파일의 스트림 및 포맷 메타데이터를 포함하는 FFmpegProbeResult
   * @throws IOException FFprobe 실행에 실패하거나 파일 조사 중 I/O 오류 발생 시
   */
  public FFmpegProbeResult getMetadata(String inputPath) throws IOException {
    // FFprobe를 사용하여 미디어 파일 정보를 조회
    return ffprobe.probe(inputPath);
  }
}
