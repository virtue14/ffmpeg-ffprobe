package com.gdpark.ffmpeg.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * STT 변환 결과를 반환하는 DTO입니다.
 */
public record TranscribeResponse(
        @Schema(description = "변환된 텍스트", example = "안녕하세요, 반가워요.") String text,

        @Schema(description = "신뢰도 (0.0 ~ 1.0)", example = "0.98") Double confidence) {
    public static TranscribeResponse of(String text) {
        return new TranscribeResponse(text, 1.0);
    }
}
