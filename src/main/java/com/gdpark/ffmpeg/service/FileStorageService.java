package com.gdpark.ffmpeg.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.UUID;

/**
 * 파일 저장 및 관리를 담당하는 서비스입니다.
 *
 * <p>업로드된 파일을 로컬 파일 시스템의 지정된 디렉토리에 저장합니다.
 */
@Service
public class FileStorageService {

  private static final Logger log = LoggerFactory.getLogger(FileStorageService.class);
  private final Path fileStorageLocation;

  /**
   * Initialize the file storage service using the provided work directory.
   *
   * Sets the storage location to the "uploads" subdirectory of the given work directory (absolute, normalized)
   * and ensures the directory exists, creating it if necessary.
   *
   * @param workDir the base work directory under which the "uploads" directory will be created
   * @throws RuntimeException if the uploads directory cannot be created
   */
  public FileStorageService(@Value("${ffmpeg.work-dir}") String workDir) {
    // 업로드 파일 저장을 위한 디렉토리 (work-dir 하위 uploads)
    this.fileStorageLocation = Paths.get(workDir, "uploads").toAbsolutePath().normalize();

    try {
      Files.createDirectories(this.fileStorageLocation);
    } catch (IOException ex) {
      throw new RuntimeException("업로드된 파일을 저장할 디렉토리를 생성할 수 없습니다.", ex);
    }
  }

  /**
   * Save an uploaded MultipartFile to the configured uploads directory and return its absolute filesystem path.
   *
   * @param file the uploaded file; its original filename will be cleaned and validated against path traversal
   * @return the absolute path of the stored file as a String
   * @throws RuntimeException if the filename contains invalid path sequences or the file cannot be written due to an I/O error
   */
  public String storeFile(MultipartFile file) {
    String originalFileName =
        StringUtils.cleanPath(Objects.requireNonNull(file.getOriginalFilename()));

    try {
      // 파일명에 부적절한 문자가 있는지 확인
      if (originalFileName.contains("..")) {
        throw new RuntimeException("파일명에 부적절한 문자가 포함되어 있습니다 " + originalFileName);
      }

      // 파일명 중복 방지를 위해 UUID 추가
      String storedFileName = UUID.randomUUID() + "_" + originalFileName;
      Path targetLocation = this.fileStorageLocation.resolve(storedFileName);

      // 디렉토리가 없을 경우 생성 (방어 코드)
      if (!Files.exists(this.fileStorageLocation)) {
        Files.createDirectories(this.fileStorageLocation);
      }

      Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);

      return targetLocation.toString();
    } catch (IOException ex) {
      throw new RuntimeException("파일 " + originalFileName + "을(를) 저장할 수 없습니다. 다시 시도해 주세요.", ex);
    }
  }
}