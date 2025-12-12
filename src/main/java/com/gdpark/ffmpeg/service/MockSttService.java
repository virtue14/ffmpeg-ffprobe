package com.gdpark.ffmpeg.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;

/**
 * 테스트용 Mock STT 서비스 구현체입니다.
 * 실제 외부 API를 호출하지 않고 더미 데이터를 반환합니다.
 */
@Service
public class MockSttService implements SttService {

    private static final Logger log = LoggerFactory.getLogger(MockSttService.class);

    @Override
    public String transcribe(File audioFile) throws IOException {
        log.info("Mock STT 요청 받음: {}", audioFile.getAbsolutePath());

        // 실제 파일 존재 여부 체크
        if (!audioFile.exists()) {
            throw new IOException("파일을 찾을 수 없습니다: " + audioFile.getAbsolutePath());
        }

        try {
            // 외부 API 호출 지연 시간 시뮬레이션 (1초)
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        log.info("Mock STT 변환 완료");
        return "이것은 테스트용 자막입니다. Mock STT 서비스가 정상적으로 동작하고 있습니다. (" + audioFile.getName() + ")";
    }
}
