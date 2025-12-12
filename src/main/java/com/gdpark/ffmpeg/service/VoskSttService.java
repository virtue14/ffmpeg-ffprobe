package com.gdpark.ffmpeg.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.vosk.LibVosk;
import org.vosk.LogLevel;
import org.vosk.Model;
import org.vosk.Recognizer;

import jakarta.annotation.PostConstruct;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Vosk 라이브러리를 사용한 오프라인 STT 서비스 구현체입니다.
 */
@Service
@Primary
public class VoskSttService implements SttService {

    private static final Logger log = LoggerFactory.getLogger(VoskSttService.class);

    private final String modelPath;
    private Model model;

    public VoskSttService(@Value("${vosk.model-path}") String modelPath) {
        this.modelPath = modelPath;
    }

    @PostConstruct
    public void init() {
        try {
            log.info("Vosk 모델 로딩 시작: {}", modelPath);
            File modelFile = new File(modelPath);

            // 상대 경로일 경우 현재 작업 디렉토리 기준 절대 경로 확인
            if (!modelFile.exists()) {
                File currentDir = new File(System.getProperty("user.dir"));
                File relativeFile = new File(currentDir, modelPath);
                if (relativeFile.exists()) {
                    modelFile = relativeFile;
                }
            }

            if (!modelFile.exists()) {
                throw new IOException("Vosk 모델을 찾을 수 없습니다: " + modelFile.getAbsolutePath());
            }

            log.info("Vosk 모델 경로 확인됨: {}", modelFile.getAbsolutePath());
            LibVosk.setLogLevel(LogLevel.WARNINGS);
            this.model = new Model(modelFile.getAbsolutePath());
            log.info("Vosk 모델 로딩 완료");
        } catch (IOException e) {
            log.error("Vosk 모델 로딩 실패. 경로를 확인해주세요: {}", modelPath, e);
            throw new RuntimeException("Vosk 모델 초기화 실패", e);
        }
    }

    @Override
    public String transcribe(File audioFile) throws IOException {
        if (model == null) {
            throw new IllegalStateException("Vosk 모델이 초기화되지 않았습니다.");
        }

        log.info("Vosk STT 변환 시작: {}", audioFile.getName());

        try (InputStream ais = new BufferedInputStream(new FileInputStream(audioFile));
                Recognizer recognizer = new Recognizer(model, 16000)) {

            int nbytes;
            byte[] b = new byte[4096];
            StringBuilder sb = new StringBuilder();

            while ((nbytes = ais.read(b)) >= 0) {
                if (recognizer.acceptWaveForm(b, nbytes)) {
                    // 중간 결과는 무시하거나 필요 시 처리
                }
            }

            // 최종 결과 반환
            String finalResult = recognizer.getFinalResult();
            log.info("Vosk STT 변환 완료");
            return finalResult;
        }
    }
}
