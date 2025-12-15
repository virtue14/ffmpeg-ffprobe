package com.gdpark.ffmpeg.dto;

/**
 * 장면 감지 결과를 담는 DTO입니다.
 *
 * @param startTime 장면 시작 시간 (초)
 * @param endTime 장면 종료 시간 (초)
 * @param clipPath 해당 장면의 비디오 클립 파일 경로
 * @param thumbnailPath 해당 장면의 대표 썸네일 파일 경로
 */
public record SceneResult(
    double startTime, double endTime, String clipPath, String thumbnailPath) {}
