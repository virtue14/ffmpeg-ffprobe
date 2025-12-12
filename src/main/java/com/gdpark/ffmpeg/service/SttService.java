package com.gdpark.ffmpeg.service;

import java.io.File;
import java.io.IOException;

/**
 * STT(Speech-to-Text) 변환 서비스를 위한 인터페이스입니다.
 * <p>
 * 다양한 STT 제공자(OpenAI Whisper, Google STT 등)로 구현체를 교체하기 용이하도록 추상화합니다.
 * </p>
 */
public interface SttService {

    /**
     * 오디오 파일을 텍스트로 변환합니다.
     *
     * @param audioFile 변환할 오디오 파일 (WAV, MP3 등)
     * @return 변환된 텍스트
     * @throws IOException 입출력 오류 발생 시
     */
    String transcribe(File audioFile) throws IOException;
}
