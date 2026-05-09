package com.ecommerce.service;

import org.springframework.web.multipart.MultipartFile;

/**
 * 文件存储服务接口
 * 当前使用本地存储实现，后续可切换为阿里云OSS:
 * 1. 添加 aliyun-oss-spring-boot-starter 依赖
 * 2. 实现 AliyunOssStorageServiceImpl
 * 3. 通过 @ConditionalOnProperty 或配置切换
 */
public interface FileStorageService {

    /**
     * 上传文件
     * @return 文件访问URL
     */
    String upload(MultipartFile file, String directory);

    /**
     * 删除文件
     */
    boolean delete(String fileUrl);
}
