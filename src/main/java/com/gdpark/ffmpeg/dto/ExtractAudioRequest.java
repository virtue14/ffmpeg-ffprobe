package com.gdpark.ffmpeg.dto;

/**
 * 오디오 추출 요청을 위한 DTO입니다.
 *
 * @param path 대상 비디오 파일 경로
 */
public record ExtractAudioRequest(
                String path) {
}
