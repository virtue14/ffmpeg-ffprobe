package com.gdpark.ffmpeg.dto;

import java.util.List;

/**
 * 장면 감지 결과 응답 DTO입니다.
 *
 * @param totalScenes 감지된 총 장면 수
 * @param scenes      개별 장면 정보 리스트
 */
public record SceneDetectionResponse(
        int totalScenes,
        List<SceneResult> scenes) {
}
