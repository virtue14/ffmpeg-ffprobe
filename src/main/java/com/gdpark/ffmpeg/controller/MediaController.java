package com.gdpark.ffmpeg.controller;

import com.gdpark.ffmpeg.dto.*;
import com.gdpark.ffmpeg.service.FileStorageService;
import com.gdpark.ffmpeg.service.MediaInfoService;
import com.gdpark.ffmpeg.service.MediaProcessingService;
import com.gdpark.ffmpeg.service.SceneDetectionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import net.bramp.ffmpeg.probe.FFmpegProbeResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/media")
@Tag(name = "미디어 컨트롤러 (MediaController)", description = "FFmpeg/FFprobe를 활용한 미디어 처리 API (메타데이터, 프레임/오디오 추출, 구간 편집 등)")
public class MediaController {

    private static final Logger log = LoggerFactory.getLogger(MediaController.class);

    private final MediaInfoService mediaInfoService;
    private final MediaProcessingService mediaProcessingService;
    private final SceneDetectionService sceneDetectionService;
    private final FileStorageService fileStorageService;

    @Autowired
    public MediaController(MediaInfoService mediaInfoService,
            MediaProcessingService mediaProcessingService,
            SceneDetectionService sceneDetectionService,
            FileStorageService fileStorageService) {
        this.mediaInfoService = mediaInfoService;
        this.mediaProcessingService = mediaProcessingService;
        this.sceneDetectionService = sceneDetectionService;
        this.fileStorageService = fileStorageService;
    }

    @Operation(summary = "파일 업로드", description = "미디어 파일을 서버에 업로드하고 저장된 절대 경로를 반환합니다. 이 경로는 다른 API의 입력값으로 사용됩니다.")
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, String>> uploadFile(
            @Parameter(description = "업로드할 미디어 파일") @RequestParam("file") MultipartFile file) {
        log.info("파일 업로드 요청: {}", file.getOriginalFilename());
        String storedPath = fileStorageService.storeFile(file);
        log.info("파일 저장 완료: {}", storedPath);
        return ResponseEntity.ok(Map.of("message", "파일 업로드 성공", "path", storedPath));
    }

    @Operation(summary = "메타데이터 조회", description = "비디오/오디오 파일의 상세 정보를 조회합니다.")
    @GetMapping("/metadata")
    public ResponseEntity<MediaMetadataResponse> getMetadata(
            @Parameter(description = "파일 경로 (서버 절대 경로)") @RequestParam String path) throws IOException {
        log.info("메타데이터 조회 요청: {}", path);
        // 실제 운영 시에는 경로 탐색(path traversal) 공격 방지를 위한 검증 필요
        FFmpegProbeResult result = mediaInfoService.getMetadata(path);

        MediaMetadataResponse response = MediaMetadataResponse.from(result);
        log.info("메타데이터 조회 완료");
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "프레임 추출", description = "지정된 FPS(초당 프레임 수)에 맞춰 이미지를 추출합니다.")
    @PostMapping("/frames")
    public ResponseEntity<Map<String, String>> extractFrames(@RequestBody ExtractFrameRequest request)
            throws IOException {
        String outputDir = mediaProcessingService.extractFrames(request.path(), request.fps());
        return ResponseEntity.ok(Map.of("message", "프레임 추출 완료", "outputDir", outputDir));
    }

    @Operation(summary = "오디오 추출", description = "영상에서 오디오 트랙을 추출하여 WAV 파일로 저장합니다.")
    @PostMapping("/audio")
    public ResponseEntity<Map<String, String>> extractAudio(@RequestBody ExtractAudioRequest request)
            throws IOException {
        String outputPath = mediaProcessingService.extractAudio(request.path());
        return ResponseEntity.ok(Map.of("message", "오디오 추출 완료", "outputPath", outputPath));
    }

    @Operation(summary = "상세 장면 분석", description = "영상 내 장면 전환을 감지하고, 각 장면의 비디오 클립과 썸네일을 생성하여 상세 정보를 반환합니다.")
    @PostMapping("/scenes")
    public ResponseEntity<List<SceneResult>> detectScenes(@RequestBody DetectSceneRequest request)
            throws IOException {
        List<SceneResult> results = sceneDetectionService.detectScenes(request.path(), request.threshold());
        return ResponseEntity.ok(results);
    }

    /**
     * 비디오 구간 자르기(Clip)
     * <p>
     * 영상의 특정 구간(시작 시간 ~ 종료 시간)을 잘라내어 새로운 파일로 저장합니다.
     * </p>
     *
     * @param request 구간 자르기 요청 정보 (경로, 시작 시간, 종료 시간)
     * @return 생성된 클립 파일 경로
     * @throws IOException FFmpeg 실행 실패 시
     */
    @Operation(summary = "비디오 구간 자르기", description = "영상의 특정 구간(시작 시간 ~ 종료 시간)을 잘라내어 새로운 비디오 파일(MP4)을 생성합니다.")
    @PostMapping("/clip")
    public ResponseEntity<Map<String, String>> createClip(@RequestBody CreateClipRequest request) throws IOException {
        // start, end를 받아서 duration 계산 후 Service 호출
        // 예시: start="00:00:30", end="00:00:45" -> duration = 15s

        long durationMs = calculateDurationMs(request.start(), request.end());

        String outputPath = mediaProcessingService.createClip(request.path(), request.start(),
                String.valueOf(durationMs / 1000));

        return ResponseEntity.ok(Map.of("message", "클립 생성 완료", "outputPath", outputPath));
    }

    /**
     * 시간 차이(Duration) 계산 헬퍼 메서드
     *
     * @param start 시작 시간 (HH:mm:ss)
     * @param end   종료 시간 (HH:mm:ss)
     * @return 시간 차이 (밀리초)
     */
    private long calculateDurationMs(String start, String end) {
        // HH:mm:ss 포맷 가정
        LocalTime startTime = LocalTime.parse(start, DateTimeFormatter.ISO_LOCAL_TIME);
        LocalTime endTime = LocalTime.parse(end, DateTimeFormatter.ISO_LOCAL_TIME);

        return Duration.between(startTime, endTime).toMillis();
    }
}
