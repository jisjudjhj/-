package com.ecommerce.service.impl;

import com.ecommerce.common.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalFileStorageServiceImplTest {

    @TempDir
    Path tempDir;

    @Test
    void uploadShouldRejectSvgFiles() {
        LocalFileStorageServiceImpl service = buildService();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "avatar.svg",
                "image/svg+xml",
                "<svg></svg>".getBytes()
        );

        assertThrows(BusinessException.class, () -> service.upload(file, "images"));
    }

    @Test
    void uploadShouldPersistAllowedImageFiles() throws Exception {
        LocalFileStorageServiceImpl service = buildService();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "avatar.png",
                "image/png",
                new byte[]{1, 2, 3}
        );

        String url = service.upload(file, "images");

        assertTrue(url.startsWith("http://example.com/uploads/images/"));
        assertTrue(Files.walk(tempDir).anyMatch(path -> path.getFileName().toString().endsWith(".png")));
    }

    private LocalFileStorageServiceImpl buildService() {
        LocalFileStorageServiceImpl service = new LocalFileStorageServiceImpl();
        ReflectionTestUtils.setField(service, "basePath", tempDir.toString());
        ReflectionTestUtils.setField(service, "baseUrl", "http://example.com/uploads");
        return service;
    }
}
