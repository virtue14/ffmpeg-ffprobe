package com.gdpark.ffmpeg.service;

import com.gdpark.ffmpeg.dto.SceneResult;
import net.bramp.ffmpeg.FFmpeg;
import net.bramp.ffmpeg.FFprobe;
import net.bramp.ffmpeg.builder.FFmpegBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class VideoClipServiceTest {

    @Mock
    private FFmpeg ffmpeg;

    @Mock
    private FFprobe ffprobe;

    private VideoClipService videoClipService;
    private final String workDir = "test-work-dir";

    @BeforeEach
    void setUp() {
        videoClipService = new VideoClipService(ffmpeg, ffprobe, workDir);
    }

    @Test
    @DisplayName("SRT 파싱 및 클립 생성 테스트")
    void createClipsFromSrt() throws IOException {
        // Given
        String inputPath = "input.mp4";
        String srtContent = """
                1
                00:00:02,520 --> 00:00:08,300
                내용 1

                2
                00:00:10,000 --> 00:00:15,000
                내용 2
                """;

        // When
        List<SceneResult> results = videoClipService.createClipsFromSrt(inputPath, srtContent);

        // Then
        assertThat(results).hasSize(2);

        // 1번 세그먼트: 2.52s ~ 8.3s (duration: 5.78s)
        SceneResult result1 = results.get(0);
        assertThat(result1.startTime()).isEqualTo(2.52);
        assertThat(result1.endTime()).isEqualTo(8.30);

        // 2번 세그먼트: 10.0s ~ 15.0s (duration: 5.0s)
        SceneResult result2 = results.get(1);
        assertThat(result2.startTime()).isEqualTo(10.0);
        assertThat(result2.endTime()).isEqualTo(15.0);

        // FFmpeg 호출 횟수 검증 (클립 2회 + 썸네일 2회 = 총 4회)
        verify(ffmpeg, times(4)).run(any(FFmpegBuilder.class), any());
    }
}
