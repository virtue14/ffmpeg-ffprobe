package com.gdpark.ffmpeg.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gdpark.ffmpeg.dto.ExtractFrameRequest;
import com.gdpark.ffmpeg.service.FileStorageService;
import com.gdpark.ffmpeg.service.MediaInfoService;
import com.gdpark.ffmpeg.service.MediaProcessingService;
import com.gdpark.ffmpeg.service.SceneDetectionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MediaController.class)
class MediaControllerTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @Autowired
    public MediaControllerTest(MockMvc mockMvc, ObjectMapper objectMapper) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
    }

    @MockBean
    private MediaInfoService mediaInfoService;

    @MockBean
    private MediaProcessingService mediaProcessingService;

    @MockBean
    private SceneDetectionService sceneDetectionService;

    @MockBean
    private FileStorageService fileStorageService;

    @Test
    @DisplayName("파일 업로드 API 테스트")
    void uploadFile() throws Exception {
        // Given
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.mp4", "video/mp4", "content".getBytes());
        given(fileStorageService.storeFile(any())).willReturn("/tmp/uploads/uuid_test.mp4");

        // When & Then
        mockMvc.perform(multipart("/media/upload")
                        .file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("파일 업로드 성공"))
                .andExpect(jsonPath("$.path").value("/tmp/uploads/uuid_test.mp4"));
    }

    @Test
    @DisplayName("프레임 추출 요청 API 테스트")
    void extractFrames() throws Exception {
        // Given
        ExtractFrameRequest request = new ExtractFrameRequest("/tmp/test.mp4", 1.0);
        given(mediaProcessingService.extractFrames(anyString(), anyDouble()))
                .willReturn("/out/frames_123");

        // When & Then
        mockMvc.perform(post("/media/frames")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("프레임 추출 완료"))
                .andExpect(jsonPath("$.outputDir").value("/out/frames_123"));
    }
}
