package com.gdpark.ffmpeg.service;

import net.bramp.ffmpeg.FFmpeg;
import net.bramp.ffmpeg.FFprobe;
import net.bramp.ffmpeg.probe.FFmpegProbeResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MediaProcessingServiceTest {

    @Mock
    private FFmpeg ffmpeg;

    @Mock
    private FFprobe ffprobe;

    @Mock
    private FFmpegProbeResult probeResult;

    private MediaProcessingService mediaProcessingService;
    private final String workDir = "test-work-dir";

    @BeforeEach
    void setUp() {
        mediaProcessingService = new MediaProcessingService(ffmpeg, ffprobe, workDir);
    }

    @Test
    @DisplayName("오디오 추출 테스트 (16kHz Mono)")
    void extractAudio() throws IOException {
        // Given
        String inputPath = "input.mp4";
        given(ffprobe.probe(inputPath)).willReturn(probeResult);

        // When
        String resultPath = mediaProcessingService.extractAudio(inputPath);

        // Then
        // 파일 이름 및 경로 검증
        assertThat(resultPath).startsWith(workDir);
        assertThat(resultPath).endsWith(".wav");

        // FFmpeg 실행 검증
        // MediaProcessingService는 내부적으로 FFmpegExecutor를 생성하고 run()을 호출합니다.
        // 최종적으로는 주입된 mock ffmpeg 객체의 run(args)가 호출됩니다.
        verify(ffmpeg).run(anyList());
    }
}
