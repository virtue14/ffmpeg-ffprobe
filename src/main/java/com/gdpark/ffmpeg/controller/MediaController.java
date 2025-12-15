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

import java.util.Map;

@RestController
@RequestMapping("/media")
@Tag(
    name = "미디어 컨트롤러 (MediaController)",
    description = "FFmpeg/FFprobe를 활용한 미디어 처리 API (메타데이터, 프레임/오디오 추출, 구간 편집 등)")
public class MediaController {

  private static final Logger log = LoggerFactory.getLogger(MediaController.class);

  private final MediaInfoService mediaInfoService;
  private final MediaProcessingService mediaProcessingService;
  private final SceneDetectionService sceneDetectionService;
  private final FileStorageService fileStorageService;

  /**
   * Create a MediaController with the required service dependencies.
   *
   * The provided services are used to handle media metadata, processing,
   * scene detection, and file storage for controller endpoints.
   */
  @Autowired
  public MediaController(
      MediaInfoService mediaInfoService,
      MediaProcessingService mediaProcessingService,
      SceneDetectionService sceneDetectionService,
      FileStorageService fileStorageService) {
    this.mediaInfoService = mediaInfoService;
    this.mediaProcessingService = mediaProcessingService;
    this.sceneDetectionService = sceneDetectionService;
    this.fileStorageService = fileStorageService;
  }

  /**
   * Handle uploading a media file and return the stored absolute path for downstream use.
   *
   * @param file the multipart media file to store
   * @return a map with keys "message" (upload status) and "path" (the stored file's absolute path)
   */
  @Operation(
      summary = "파일 업로드",
      description = "미디어 파일을 서버에 업로드하고 저장된 절대 경로를 반환합니다. 이 경로는 다른 API의 입력값으로 사용됩니다.")
  @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<Map<String, String>> uploadFile(
      @Parameter(description = "업로드할 미디어 파일") @RequestParam("file") MultipartFile file) {
    log.info("파일 업로드 요청: {}", file.getOriginalFilename());
    String storedPath = fileStorageService.storeFile(file);
    log.info("파일 저장 완료: {}", storedPath);
    return ResponseEntity.ok(Map.of("message", "파일 업로드 성공", "path", storedPath));
  }

  /**
   * Retrieve detailed metadata for a media file at the given server path.
   *
   * @param path absolute server file path of the media file to probe
   * @return a MediaMetadataResponse containing format, streams, and other probe information
   * @throws IOException if probing the file fails or an I/O error occurs
   */
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

  /**
   * Extracts the audio track from the specified video and saves it as a WAV file.
   *
   * @param request request containing the source video's file path
   * @return a map with keys "message" (operation status) and "outputPath" (path to the saved WAV file)
   * @throws IOException if audio extraction or file I/O fails
   */
  @Operation(summary = "오디오 추출", description = "영상에서 오디오 트랙을 추출하여 WAV 파일로 저장합니다.")
  @PostMapping("/audio")
  public ResponseEntity<Map<String, String>> extractAudio(@RequestBody ExtractAudioRequest request)
      throws IOException {
    String outputPath = mediaProcessingService.extractAudio(request.path());
    return ResponseEntity.ok(Map.of("message", "오디오 추출 완료", "outputPath", outputPath));
  }

  /**
   * Detects scene boundaries in the specified media file and returns detailed detection results.
   *
   * @param request the request containing the media file path and the threshold used to determine scene changes
   * @return a SceneDetectionResponse containing detected scenes and associated generated clips and thumbnails
   * @throws IOException if reading or processing the media file fails
   */
  @Operation(
      summary = "상세 장면 분석",
      description = "영상 내 장면 전환을 감지하고, 각 장면의 비디오 클립과 썸네일을 생성하여 상세 정보를 반환합니다.")
  @PostMapping("/scenes")
  public ResponseEntity<SceneDetectionResponse> detectScenes(
      @RequestBody DetectSceneRequest request) throws IOException {
    SceneDetectionResponse response =
        sceneDetectionService.detectScenes(request.path(), request.threshold());
    return ResponseEntity.ok(response);
  }
}