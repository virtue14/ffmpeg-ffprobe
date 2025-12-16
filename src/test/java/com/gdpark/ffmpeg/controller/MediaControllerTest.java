package com.gdpark.ffmpeg.controller;

import com.gdpark.ffmpeg.service.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MediaController.class)
class MediaControllerTest {

    private MockMvc mockMvc;

    @Autowired
    public MediaControllerTest(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    @MockBean
    private MediaInfoService mediaInfoService;

    @MockBean
    private MediaProcessingService mediaProcessingService;

    @MockBean
    private SceneDetectionService sceneDetectionService;

    @MockBean
    private FileStorageService fileStorageService;

    @MockBean
    private SttService sttService;

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

}
