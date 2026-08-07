package com.mtole.taskmanager.files;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;

@RestController
@RequestMapping("/files")
public class FileUploadController {

    private final FileUploadService fileUploadService;
    private final Duration downloadUrlTtl;

    public FileUploadController(FileUploadService fileUploadService,
                                @Value("${aws.s3.presign.download-ttl}") Duration downloadUrlTtl) {
        this.fileUploadService = fileUploadService;
        this.downloadUrlTtl = downloadUrlTtl;
    }

    @PostMapping
    public UploadResponse upload(@RequestParam("file") MultipartFile file) throws IOException {
        return new UploadResponse(fileUploadService.upload(file));
    }

    @GetMapping("/download-url")
    public PresignedUrlResponse getFile(@RequestParam String key) {
        return fileUploadService.generateDownloadUrl(key,downloadUrlTtl);
    }

}
