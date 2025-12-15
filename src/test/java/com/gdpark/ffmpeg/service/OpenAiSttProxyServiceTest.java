package com.gdpark.ffmpeg.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@DisplayName("OpenAiSttProxyService 테스트")
class OpenAiSttProxyServiceTest {

    private OpenAiSttProxyService openAiSttProxyService;
    private MockRestServiceServer mockServer;

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplate();
        RestTemplateBuilder builder = new RestTemplateBuilder() {
            @Override
            public RestTemplate build() {
                return restTemplate;
            }
        };

        mockServer = MockRestServiceServer.bindTo(restTemplate).build();
        openAiSttProxyService = new OpenAiSttProxyService("http://test-server/api/stt", builder);
    }

    @Test
    @DisplayName("STT 요청 성공 테스트")
    void transcribeSuccess() throws IOException {
        // Given
        File tempFile = File.createTempFile("test-audio", ".wav");
        Files.writeString(tempFile.toPath(), "dummy content");
        String expectedText = "Hello, this is a test.";

        mockServer.expect(requestTo("http://test-server/api/stt"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(expectedText, MediaType.TEXT_PLAIN));

        // When
        String result = openAiSttProxyService.transcribe(tempFile);

        // Then
        assertThat(result).isEqualTo(expectedText);
        assertThat(openAiSttProxyService).isInstanceOf(SttService.class);
        mockServer.verify();

        // Cleanup
        tempFile.delete();
    }
}
