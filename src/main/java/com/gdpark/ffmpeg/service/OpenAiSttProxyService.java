package com.gdpark.ffmpeg.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.io.File;

/**
 * backendcore(외부 시스템)로 오디오 파일을 전송하여 OpenAI STT 기능을 대행하는 서비스입니다.
 */
@Service
@Primary
public class OpenAiSttProxyService implements SttService {

    private static final Logger log = LoggerFactory.getLogger(OpenAiSttProxyService.class);

    private final String backendcoreUrl;
    private final RestTemplate restTemplate;

    @Autowired
    public OpenAiSttProxyService(@Value("${external.backendcore.stt-url}") String backendcoreUrl,
            RestTemplateBuilder restTemplateBuilder) {
        this.backendcoreUrl = backendcoreUrl;
        this.restTemplate = restTemplateBuilder.build();
    }

    /**
     * 오디오 파일을 backendcore로 전송하고 변환된 텍스트를 반환합니다.
     *
     * @param audioFile 변환할 오디오 파일 (WAV 등)
     * @return STT 변환 텍스트
     */
    @Override
    public String transcribe(File audioFile) {
        log.info("backendcore로 STT 요청 전송: URL={}, File={}", backendcoreUrl, audioFile.getName());

        if (!audioFile.exists()) {
            throw new IllegalArgumentException("전송할 파일이 존재하지 않습니다: " + audioFile.getAbsolutePath());
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", new FileSystemResource(audioFile));
            body.add("language", "ko");
            body.add("responseFormat", "json");

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(backendcoreUrl, requestEntity, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("backendcore STT 응답 수신 완료");
                return response.getBody();
            } else {
                log.error("backendcore 요청 실패: Status={}", response.getStatusCode());
                throw new RuntimeException("External STT Service failed with status: " + response.getStatusCode());
            }

        } catch (Exception e) {
            log.error("backendcore 통신 중 오류 발생", e);
            throw new RuntimeException("Failed to call external STT service", e);
        }
    }
}
