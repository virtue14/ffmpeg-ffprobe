package com.gdpark.ffmpeg.controller;

import com.gdpark.ffmpeg.dto.*;
import com.gdpark.ffmpeg.service.FileStorageService;
import com.gdpark.ffmpeg.service.MediaInfoService;
import com.gdpark.ffmpeg.service.MediaProcessingService;
import com.gdpark.ffmpeg.service.SceneDetectionService;
import com.gdpark.ffmpeg.service.SttService;
import com.gdpark.ffmpeg.service.VideoClipService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import net.bramp.ffmpeg.probe.FFmpegProbeResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

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
  private final SttService sttService;
  private final VideoClipService videoClipService;

  @Autowired
  public MediaController(
      MediaInfoService mediaInfoService,
      MediaProcessingService mediaProcessingService,
      SceneDetectionService sceneDetectionService,
      FileStorageService fileStorageService,
      SttService sttService,
      VideoClipService videoClipService) {
    this.mediaInfoService = mediaInfoService;
    this.mediaProcessingService = mediaProcessingService;
    this.sceneDetectionService = sceneDetectionService;
    this.fileStorageService = fileStorageService;
    this.sttService = sttService;
    this.videoClipService = videoClipService;
  }

  /**
   * 미디어 파일을 업로드하고, 이후 작업에서 사용할 수 있도록 저장된 절대 경로를 반환합니다.
   *
   * @param file 저장할 멀티파트 미디어 파일
   * @return 업로드 상태("message")와 저장된 파일의 절대 경로("path")를 담은 맵
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
   * 지정된 서버 경로에 있는 미디어 파일의 상세 메타데이터를 조회합니다.
   *
   * @param path 조사할 미디어 파일의 서버 내 절대 경로
   * @return 포맷, 스트림 및 기타 조사 정보를 포함하는 MediaMetadataResponse
   * @throws IOException 파일 조사에 실패하거나 I/O 오류가 발생한 경우
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
   * 지정된 비디오에서 오디오 트랙을 추출하여 WAV 파일로 저장합니다.
   *
   * @param request 원본 비디오 파일 경로를 포함하는 요청 객체
   * @return 작업 상태("message")와 저장된 WAV 파일 경로("outputPath")를 담은 맵
   * @throws IOException 오디오 추출 또는 파일 I/O 실패 시
   */
  @Operation(summary = "오디오 추출", description = "영상에서 오디오 트랙을 추출하여 WAV 파일로 저장합니다.")
  @PostMapping("/audio")
  public ResponseEntity<Map<String, String>> extractAudio(@RequestBody ExtractAudioRequest request)
      throws IOException {
    String outputPath = mediaProcessingService.extractAudio(request.path());
    return ResponseEntity.ok(Map.of("message", "오디오 추출 완료", "outputPath", outputPath));
  }

  /**
   * 지정된 미디어 파일에서 장면 경계를 감지하고 상세 감지 결과를 반환합니다.
   *
   * @param request 미디어 파일 경로와 장면 전환 판단에 사용될 임계값을 포함하는 요청 객체
   * @return 감지된 장면들과 각 장면의 클립 및 썸네일 정보를 포함하는 SceneDetectionResponse
   * @throws IOException 미디어 파일을 읽거나 처리하는 중 오류 발생 시
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

  /**
   * 저장된 비디오 파일의 경로를 입력받아 오디오를 추출하고, 텍스트로 변환(STT)합니다.
   * 또한 변환된 자막(SRT)을 기반으로 비디오 클립을 생성합니다.
   *
   * @param request 비디오 파일 경로를 포함하는 요청 객체
   * @return 변환된 텍스트와 생성된 클립 정보를 포함하는 TranscribeResponse
   * @throws IOException 오디오 추출 또는 파일 처리 중 오류 발생 시
   */
  @Operation(summary = "STT 변환", description = "서버에 저장된 비디오 파일의 경로를 입력받아 오디오를 추출하고, 텍스트로 변환합니다.")
  @PostMapping("/stt")
  public ResponseEntity<TranscribeResponse> transcribeVideo(
      @RequestBody TranscribeVideoRequest request) throws IOException {
    String storedPath = request.path();
    log.info("STT 요청: {}", storedPath);

    // 오디오 추출 (WAV)
    String audioPath = mediaProcessingService.extractAudio(storedPath);

    // STT 변환
    java.io.File audioFile = new java.io.File(audioPath);
    String text = sttService.transcribe(audioFile);

    // 클립 생성 (SRT 파싱 및 비디오 자르기)
    log.info("SRT 기반 클립 생성 시작");
    List<SceneResult> scenes = videoClipService.createClipsFromSrt(storedPath, text);

    log.info("STT 변환 및 클립 생성 완료: {} scenes", scenes.size());
    return ResponseEntity.ok(TranscribeResponse.of(text, scenes));
  }
}
