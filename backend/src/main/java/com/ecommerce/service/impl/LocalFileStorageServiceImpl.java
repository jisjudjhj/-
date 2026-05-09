package com.ecommerce.service.impl;

import com.ecommerce.common.BusinessException;
import com.ecommerce.service.FileStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Service
@ConditionalOnProperty(name = "storage.type", havingValue = "local", matchIfMissing = true)
public class LocalFileStorageServiceImpl implements FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(LocalFileStorageServiceImpl.class);

    private static final Set<String> ALLOWED_EXTENSIONS = new HashSet<>(Arrays.asList(
            ".jpg", ".jpeg", ".png", ".gif", ".webp", ".bmp"));

    private static final long MAX_FILE_SIZE = 20 * 1024 * 1024;

    @Value("${storage.local.base-path:./uploads}")
    private String basePath;

    @Value("${storage.local.base-url:http://localhost:8080/uploads}")
    private String baseUrl;

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
        String fileName = UUID.randomUUID().toString().replace("-", "") + ext;
        String relativePath = directory + "/" + datePath + "/" + fileName;

        File dest = new File(basePath + "/" + relativePath);
        if (!dest.getParentFile().exists()) {
            dest.getParentFile().mkdirs();
        }

        try {
            file.transferTo(dest);
            log.info("[文件上传] 成功: {}", relativePath);
            return baseUrl + "/" + relativePath;
        } catch (IOException e) {
            log.error("[文件上传] 失败: {}", e.getMessage());
            throw new BusinessException("文件上传失败");
        }
    }

    @Override
    public boolean delete(String fileUrl) {
        if (fileUrl == null || !fileUrl.startsWith(baseUrl)) {
            return false;
        }
        String relativePath = fileUrl.replace(baseUrl + "/", "");
        File file = new File(basePath + "/" + relativePath);
        if (file.exists()) {
            boolean result = file.delete();
            log.info("[文件删除] {}: {}", result ? "成功" : "失败", relativePath);
            return result;
        }
        return false;
    }
}
