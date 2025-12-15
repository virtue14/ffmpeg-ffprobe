package com.gdpark.ffmpeg.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class FileStorageServiceTest {

  private FileStorageService fileStorageService;

  @TempDir Path tempDir;

  @BeforeEach
  void setUp() {
    // 임시 디렉토리를 작업 디렉토리로 설정
    fileStorageService = new FileStorageService(tempDir.toString());
  }

  @Test
  @DisplayName("파일 업로드 저장 테스트")
  void storeFile() throws IOException {
    // Given
    String fileName = "test-video.mp4";
    String content = "dummy content";
    MockMultipartFile file =
        new MockMultipartFile("file", fileName, "video/mp4", content.getBytes());

    // When
    String storedPath = fileStorageService.storeFile(file);

    // Then
    Path path = Path.of(storedPath);
    assertThat(Files.exists(path)).isTrue();
    assertThat(Files.readAllLines(path).get(0)).isEqualTo(content);
    assertThat(path.getParent()).isEqualTo(tempDir.resolve("uploads"));
    assertThat(path.getFileName().toString()).contains(fileName); // UUID가 붙어있는지 확인
  }
}
