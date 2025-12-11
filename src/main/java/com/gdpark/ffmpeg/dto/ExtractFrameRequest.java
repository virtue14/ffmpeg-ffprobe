package com.gdpark.ffmpeg.dto;

/**
 * 프레임 추출 요청을 위한 DTO입니다.
 *
 * @param path 대상 비디오 파일 경로
 * @param fps  초당 추출할 프레임 수
 */
public record ExtractFrameRequest(
                String path,
                double fps) {
}
