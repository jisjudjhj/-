package com.ecommerce.service.impl;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.ObjectMetadata;
import com.ecommerce.common.BusinessException;
import com.ecommerce.service.FileStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.PreDestroy;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@ConditionalOnProperty(name = "storage.type", havingValue = "oss")
public class AliyunOssStorageServiceImpl implements FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(AliyunOssStorageServiceImpl.class);

    private static final Set<String> ALLOWED_EXTENSIONS = new HashSet<>(Arrays.asList(
            ".jpg", ".jpeg", ".png", ".gif", ".webp", ".bmp", ".svg",
            ".mp4", ".pdf", ".doc", ".docx", ".xls", ".xlsx"));

    private static final long MAX_FILE_SIZE = 20 * 1024 * 1024;

    @Value("${aliyun.oss.endpoint:}")
    private String endpoint;

    @Value("${aliyun.oss.access-key-id:}")
    private String accessKeyId;

    @Value("${aliyun.oss.access-key-secret:}")
    private String accessKeySecret;

    @Value("${aliyun.oss.bucket-name:}")
    private String bucketName;

    @Value("${aliyun.oss.url-prefix:}")
    private String baseUrl;

    private volatile OSS ossClient;

    private OSS getOssClient() {
        if (ossClient == null) {
            synchronized (this) {
                if (ossClient == null) {
                    if (endpoint.isEmpty() || accessKeyId.isEmpty() || accessKeySecret.isEmpty() || bucketName.isEmpty()) {
                        throw new BusinessException("OSS配置不完整，请检查 aliyun.oss 相关配置");
                    }
                    ossClient = new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);
                    log.info("[AliyunOSS] 客户端初始化成功, endpoint={}, bucket={}", endpoint, bucketName);
                }
            }
        }
        return ossClient;
    }

    @PreDestroy
    public void destroy() {
        if (ossClient != null) {
            ossClient.shutdown();
            log.info("[AliyunOSS] 客户端已关闭");
        }
    }

    @Override
    public String upload(MultipartFile file, String directory) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("上传文件不能为空");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException("文件大小不能超过20MB");
        }

        String originalName = file.getOriginalFilename();
        String ext = "";
        if (originalName != null && originalName.contains(".")) {
            ext = originalName.substring(originalName.lastIndexOf(".")).toLowerCase();
        }
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new BusinessException("不支持的文件格式，仅允许: " + ALLOWED_EXTENSIONS);
        }

        String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        String objectKey = directory + "/" + datePath + "/"
                + UUID.randomUUID().toString().replace("-", "") + ext;

        try {
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentType(resolveContentType(file.getContentType(), ext));
            metadata.setContentLength(file.getSize());
            metadata.setContentDisposition("inline");
            metadata.setCacheControl("public, max-age=31536000");

            getOssClient().putObject(bucketName, objectKey, file.getInputStream(), metadata);
            String normalizedBaseUrl = getBaseUrl();
            String url = normalizedBaseUrl.endsWith("/") ? normalizedBaseUrl + objectKey : normalizedBaseUrl + "/" + objectKey;
            log.info("[AliyunOSS] 上传成功: {}", url);
            return url;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("[AliyunOSS] 上传失败: {}", e.getMessage(), e);
            throw new BusinessException("文件上传失败: " + e.getMessage());
        }
    }

    @Override
    public boolean delete(String fileUrl) {
        String normalizedBaseUrl = getBaseUrl();
        if (fileUrl == null || !fileUrl.startsWith(normalizedBaseUrl)) {
            return false;
        }
        String objectKey = fileUrl.replace(normalizedBaseUrl.endsWith("/") ? normalizedBaseUrl : normalizedBaseUrl + "/", "");
        try {
            getOssClient().deleteObject(bucketName, objectKey);
            log.info("[AliyunOSS] 删除成功: {}", objectKey);
            return true;
        } catch (Exception e) {
            log.error("[AliyunOSS] 删除失败: {}", e.getMessage());
            return false;
        }
    }

    private String getBaseUrl() {
        if (baseUrl != null && !baseUrl.trim().isEmpty()) {
            return baseUrl.trim();
        }
        return "https://" + bucketName + "." + endpoint;
    }

    private String resolveContentType(String contentType, String extension) {
        if (contentType != null && !contentType.trim().isEmpty()) {
            return contentType;
        }

        String ext = extension == null ? "" : extension.toLowerCase(Locale.ROOT);
        switch (ext) {
            case ".jpg":
            case ".jpeg":
                return "image/jpeg";
            case ".png":
                return "image/png";
            case ".gif":
                return "image/gif";
            case ".webp":
                return "image/webp";
            case ".bmp":
                return "image/bmp";
            case ".svg":
                return "image/svg+xml";
            case ".mp4":
                return "video/mp4";
            case ".pdf":
                return "application/pdf";
            case ".doc":
                return "application/msword";
            case ".docx":
                return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case ".xls":
                return "application/vnd.ms-excel";
            case ".xlsx":
                return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            default:
                return "application/octet-stream";
        }
    }
}
