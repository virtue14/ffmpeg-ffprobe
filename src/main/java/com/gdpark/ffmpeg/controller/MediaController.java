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
   * Create a MediaController with its required service dependencies.
   *
   * @param mediaInfoService        service used to retrieve media metadata
   * @param mediaProcessingService  service used to perform media processing tasks (e.g., audio extraction)
   * @param sceneDetectionService   service used to detect scenes in media files
   * @param fileStorageService      service used to store uploaded files and provide storage paths
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
   * Uploads a media file and stores it on the server.
   *
   * The response body includes a confirmation message and the stored file's absolute path for use with other APIs.
   *
   * @param file the media file to upload
   * @return a ResponseEntity whose body is a map with keys "message" (confirmation text) and "path" (the stored absolute file path)
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
   * Retrieve detailed metadata for a video or audio file.
   *
   * @param path the server absolute path to the media file
   * @return a MediaMetadataResponse containing parsed metadata (streams, format, duration, etc.)
   * @throws IOException if probing the media file fails or IO error occurs during metadata retrieval
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
   * Extracts the audio track from the media at the provided path and saves it as a WAV file.
   *
   * @param request request containing the path to the source media file
   * @return a map with keys "message" and "outputPath"; "outputPath" is the saved WAV file path
   * @throws IOException if audio extraction or file operations fail
   */
  @Operation(summary = "오디오 추출", description = "영상에서 오디오 트랙을 추출하여 WAV 파일로 저장합니다.")
  @PostMapping("/audio")
  public ResponseEntity<Map<String, String>> extractAudio(@RequestBody ExtractAudioRequest request)
      throws IOException {
    String outputPath = mediaProcessingService.extractAudio(request.path());
    return ResponseEntity.ok(Map.of("message", "오디오 추출 완료", "outputPath", outputPath));
  }

  /**
   * Detects scene boundaries in a video and produces a detailed response that includes per-scene clips and thumbnails.
   *
   * @param request contains the input media path and the sensitivity threshold used for scene detection
   * @return a SceneDetectionResponse containing detected scenes with their metadata, generated clip paths, and thumbnail paths
   * @throws IOException if an I/O error occurs while processing the media
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