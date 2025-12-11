package com.gdpark.ffmpeg.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

/**
 * FFmpeg 관련 설정 속성을 정의하는 레코드입니다.
 * <p>
 * `application.yml`의 `ffmpeg` 프리픽스를 가진 설정값들과 매핑됩니다.
 * </p>
 *
 * @param ffmpegPath  FFmpeg 실행 파일 경로
 * @param ffprobePath FFprobe 실행 파일 경로
 * @param workDir     미디어 처리 작업이 이루어질 작업 디렉토리 경로
 */
@ConfigurationProperties(prefix = "ffmpeg")
public record FfmpegProperties(
        String ffmpegPath,
        String ffprobePath,
        String workDir) {
}
