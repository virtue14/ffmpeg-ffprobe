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

  /**
   * 스프링 부트 애플리케이션을 시작하고 Smile ML 루틴을 실행하는 진입점입니다.
   *
   * <p>애플리케이션 컨텍스트를 시작하고 Smile ML 루틴을 실행하며, 시작과 종료 마커를 출력합니다. 오류 발생 시 예외 메시지를 표준 에러로 출력합니다.
   *
   * @param args 애플리케이션에 전달되는 명령줄 인수
   */
  public static void main(String[] args) {
    SpringApplication.run(FfmpegFfprobeApplication.class, args);

    Smile smile = new Smile();

    try {
      System.out.println("--- Smile ML Start");
      smile.smileRun();
      System.out.println("--- Smile ML End");
    } catch (Exception e) {
      System.err.println("--- Smile ML Error" + e.getMessage());
    }
  }
}
