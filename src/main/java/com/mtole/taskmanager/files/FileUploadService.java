package com.mtole.taskmanager.files;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
public class FileUploadService {

    private static final DateTimeFormatter DATE_PREFIX = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final String bucket;

    public FileUploadService(S3Client s3Client, S3Presigner s3Presigner,
                             @Value("${aws.s3.bucket}") String bucket) {
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
        this.bucket = bucket;
    }


    public String upload(MultipartFile file) throws IOException {
        String key = buildKey(file.getOriginalFilename());

        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(file.getContentType())
                .build();

        s3Client.putObject(request, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));

        return key;
    }

    public PresignedUrlResponse generateDownloadUrl(String key, Duration ttl) {
        // 1. Describe la operación pura: "GET del objeto K del bucket B"
        GetObjectRequest objectRequest = GetObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build();

        // 2. Envuelve esa operación con las opciones de firmado
        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(ttl)
                .getObjectRequest(objectRequest)
                .build();

        // 3. Pídele al presigner que firme (cálculo local, sin red)
        PresignedGetObjectRequest presigned = s3Presigner.presignGetObject(presignRequest);

        // 4. Empaqueta URL + expiración en el DTO de respuesta
        return new PresignedUrlResponse(
                key,
                presigned.url().toString(),
                presigned.expiration()
        );
    }




    private String buildKey(String originalFilename) {
        String datePrefix = LocalDate.now().format(DATE_PREFIX);
        String uuid = UUID.randomUUID().toString();
        String safeName = sanitize(originalFilename);
        return "%s/%s-%s".formatted(datePrefix, uuid, safeName);
    }

    private String sanitize(String filename) {
        if (filename == null || filename.isBlank()) {
            return "file";
        }
        return filename.replaceAll("[^a-zA-Z0-9._-]", "-");
    }
}

