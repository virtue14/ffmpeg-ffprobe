package com.gdpark.ffmpeg.dto;

/**
 * 비디오 구간 자르기(Clip) 요청을 위한 DTO입니다.
 *
 * @param path  대상 비디오 파일 경로
 * @param start 시작 시간 (예: "00:00:10" 또는 초 단위)
 * @param end   종료 시간 (예: "00:00:20" 또는 초 단위)
 */
public record CreateClipRequest(
                String path,
                String start,
                String end) {
}
