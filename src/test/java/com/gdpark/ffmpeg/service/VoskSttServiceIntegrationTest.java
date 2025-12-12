package com.gdpark.ffmpeg.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.io.File;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@SpringBootTest
class VoskSttServiceIntegrationTest {

    @Autowired(required = false)
    private VoskSttService voskSttService;

    @Value("${vosk.model-path:}")
    private String modelPath;

    @Test
    @DisplayName("Vosk 모델 로딩 및 STT 변환 통합 테스트")
    void transcribe_integration() throws IOException {
        // Given
        // 1. 모델 경로 유효성 확인 (실제 모델이 없으면 테스트 스킵)
        File modelFile = new File(modelPath);
        if (!modelFile.exists()) {
            // 상대 경로 체크 (사용자 편의)
            modelFile = new File(System.getProperty("user.dir"), modelPath);
        }

        boolean modelExists = modelFile.exists() && modelFile.isDirectory();
        assumeTrue(modelExists, "Vosk 모델 파일이 존재하지 않아 통합 테스트를 건너뜁니다. Path: " + modelPath);
        assumeTrue(voskSttService != null, "VoskSttService Bean이 생성되지 않았습니다.");

        // 2. 테스트용 오디오 파일 확인 (없으면 스킵)
        // src/test/resources에 sample.wav가 있다고 가정하거나, 없으면 스킵
        File sampleAudio = new File("src/test/resources/sample_16k.wav");
        assumeTrue(sampleAudio.exists(), "테스트용 오디오 파일(sample_16k.wav)이 존재하지 않아 건너뜁니다.");

        // When
        String result = voskSttService.transcribe(sampleAudio);

        // Then
        assertThat(result).isNotNull();
        System.out.println("STT Result: " + result);
    }
}
