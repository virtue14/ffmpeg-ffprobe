package com.gdpark.ffmpeg.config;

import net.bramp.ffmpeg.FFmpeg;
import net.bramp.ffmpeg.FFprobe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.File;
import java.io.IOException;

/**
 * FFmpeg/FFprobe 빈(Bean) 설정 클래스입니다.
 * <p>
 * 라이브러리 wrapper 객체를 빈으로 등록하고, 작업 디렉토리를 초기화합니다.
 * </p>
 */
@Configuration
@EnableConfigurationProperties(FfmpegProperties.class)
public class FfmpegConfig {

    private static final Logger log = LoggerFactory.getLogger(FfmpegConfig.class);

    private final FfmpegProperties ffmpegProperties;

    @Autowired
    public FfmpegConfig(FfmpegProperties ffmpegProperties) {
        this.ffmpegProperties = ffmpegProperties;
    }

    /**
     * FFmpeg wrapper 객체를 빈으로 등록합니다.
     *
     * @return 설정된 FFmpeg 객체
     * @throws IOException 실행 파일을 찾을 수 없거나 초기화 실패 시 발생
     */
    @Bean
    public FFmpeg ffmpeg() throws IOException {
        String path = ffmpegProperties.ffmpegPath();
        if (path == null || path.isBlank()) {
            path = "ffmpeg"; // 시스템 경로 시도
        }
        log.info("FFmpeg 초기화: Path={}", path);
        return new FFmpeg(path);
    }

    /**
     * FFprobe wrapper 객체를 빈으로 등록합니다.
     *
     * @return 설정된 FFprobe 객체
     * @throws IOException 실행 파일을 찾을 수 없거나 초기화 실패 시 발생
     */
    @Bean
    public FFprobe ffprobe() throws IOException {
        String path = ffmpegProperties.ffprobePath();
        if (path == null || path.isBlank()) {
            path = "ffprobe"; // 시스템 경로 시도
        }
        log.info("FFprobe 초기화: Path={}", path);
        return new FFprobe(path);
    }

    /**
     * 작업 디렉토리 경로 빈을 등록합니다.
     * <p>
     * 빈 등록 시점에 해당 디렉토리가 존재하지 않으면 생성을 시도합니다.
     * </p>
     *
     * @return 작업 디렉토리 경로 (String)
     */
    @Bean
    public String workDir() {
        String path = ffmpegProperties.workDir();
        File dir = new File(path);
        if (!dir.exists()) {
            boolean created = dir.mkdirs();
            log.info("작업 디렉토리 생성: {} (성공여부: {})", path, created);
        } else {
            log.info("작업 디렉토리 확인: {}", path);
        }
        return path;
    }
}
