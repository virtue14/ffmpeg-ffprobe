package com.gdpark.ffmpeg.dto;

import net.bramp.ffmpeg.probe.FFmpegProbeResult;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 미디어 메타데이터 상세 정보를 담는 응답 DTO입니다.
 * <p>
 * `FFmpegProbeResult`의 복잡한 구조를 클라이언트 친화적인 형태로 단순화했습니다.
 * </p>
 *
 * @param filename 파일명
 * @param duration 재생 길이 (초)
 * @param size     파일 크기 (bytes)
 * @param bitRate  비트레이트
 * @param format   파일 포맷 정보
 * @param streams  포함된 스트림(Video/Audio) 정보 리스트
 */
public record MediaMetadataResponse(
                String filename,
                double duration,
                long size,
                long bitRate,
                Format format,
                List<Stream> streams) {

        /**
         * `FFmpegProbeResult` 엔티티를 `MediaMetadataResponse` DTO로 변환합니다.
         *
         * @param result FFprobe 실행 결과 객체
         * @return 변환된 DTO 객체
         */
        public static MediaMetadataResponse from(FFmpegProbeResult result) {
                return new MediaMetadataResponse(
                                result.getFormat().filename,
                                result.getFormat().duration,
                                result.getFormat().size,
                                result.getFormat().bit_rate,
                                new Format(result.getFormat().format_name, result.getFormat().format_long_name),
                                result.getStreams().stream()
                                                .map(s -> new Stream(
                                                                s.codec_name,
                                                                s.codec_long_name,
                                                                s.codec_type.name(),
                                                                s.width,
                                                                s.height,
                                                                s.avg_frame_rate != null ? s.avg_frame_rate.toString()
                                                                                : "N/A"))
                                                .collect(Collectors.toList()));
        }

        /**
         * 포맷 상세 정보
         * 
         * @param name     포맷 이름 (짧은 이름)
         * @param longName 포맷 전체 이름
         */
        public record Format(String name, String longName) {
        }

        /**
         * 스트림 상세 정보
         * 
         * @param codecName     코덱 이름
         * @param codecLongName 코덱 전체 이름
         * @param codecType     스트림 타입 (VIDEO, AUDIO 등)
         * @param width         해상도 너비 (비디오일 경우)
         * @param height        해상도 높이 (비디오일 경우)
         * @param frameRate     프레임 레이트
         */
        public record Stream(
                        String codecName,
                        String codecLongName,
                        String codecType,
                        int width,
                        int height,
                        String frameRate) {
        }
}
