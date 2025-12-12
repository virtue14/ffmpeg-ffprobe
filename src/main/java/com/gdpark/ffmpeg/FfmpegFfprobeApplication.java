package com.gdpark.ffmpeg;

import com.gdpark.ffmpeg.util.Smile;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * FFmpeg/FFprobe 기반 미디어 처리 애플리케이션의 엔트리 포인트입니다.
 *
 * <p>Spring Boot 애플리케이션을 구동하며, 시작 시 Smile ML 테스트 코드를 실행합니다.
 */
@SpringBootApplication
public class FfmpegFfprobeApplication {

  public static void main(String[] args) {
    SpringApplication.run(FfmpegFfprobeApplication.class, args);

    Smile smile = new Smile();

    try {
      System.out.println("--- Smile ML Start");
      smile.smileRun();
      System.out.println("--- Smile ML End");
    } catch (Exception e) {
      System.out.println("--- Smile ML Error" + e.getMessage());
    }
  }
}
