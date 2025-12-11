package com.gdpark.ffmpeg.dto;

/**
 * 장면 감지 요청을 위한 DTO입니다.
 *
 * @param path      대상 비디오 파일 경로
 * @param threshold 장면 변화 감지 임계값 (0.0 ~ 1.0, 권장값: 0.3)
 */
public record DetectSceneRequest(
                String path,
                double threshold) {
}
