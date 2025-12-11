package com.gdpark.ffmpeg;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

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
