package com.gdpark.ffmpeg.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * STT 변환 결과를 반환하는 DTO입니다.
 */
public record TranscribeResponse(
        @Schema(description = "변환된 텍스트", example = "안녕하세요, 반가워요.") String text,
        @Schema(description = "생성된 장면 클립 정보") List<SceneResult> scenes) {

    public static TranscribeResponse of(String text) {
        return new TranscribeResponse(text, null);
    }

    public static TranscribeResponse of(String text, List<SceneResult> scenes) {
        return new TranscribeResponse(text, scenes);
    }
}
