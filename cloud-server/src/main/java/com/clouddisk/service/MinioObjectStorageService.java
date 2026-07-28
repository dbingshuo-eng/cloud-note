package com.clouddisk.service;

import com.clouddisk.config.MinioProperties;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

import java.io.InputStream;

@Service
public class MinioObjectStorageService implements ObjectStorageService {

    private static final String SAFE_FAILURE_MESSAGE = "Object storage unavailable";

    private final MinioClient minioClient;
    private final MinioProperties minioProperties;

    public MinioObjectStorageService(MinioClient minioClient, MinioProperties minioProperties) {
        this.minioClient = minioClient;
        this.minioProperties = minioProperties;
    }

    @Override
    public void upload(String objectKey, InputStream inputStream, long size, String contentType) {
        try {
            if (!minioClient.bucketExists(BucketExistsArgs.builder().bucket(minioProperties.getBucket()).build())) {
                throw new ObjectStorageException(SAFE_FAILURE_MESSAGE);
            }

            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(minioProperties.getBucket())
                    .object(objectKey)
                    .stream(inputStream, size, -1)
                    .contentType(contentType == null || contentType.isBlank()
                            ? MediaType.APPLICATION_OCTET_STREAM_VALUE
                            : contentType)
                    .build());
        } catch (ObjectStorageException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ObjectStorageException(SAFE_FAILURE_MESSAGE, exception);
        }
    }

    @Override
    public void stat(String objectKey) {
        try {
            minioClient.statObject(StatObjectArgs.builder()
                    .bucket(minioProperties.getBucket())
                    .object(objectKey)
                    .build());
        } catch (Exception exception) {
            throw new ObjectStorageException(SAFE_FAILURE_MESSAGE, exception);
        }
    }

    @Override
    public InputStream download(String objectKey) {
        try {
            return minioClient.getObject(GetObjectArgs.builder()
                    .bucket(minioProperties.getBucket())
                    .object(objectKey)
                    .build());
        } catch (Exception exception) {
            throw new ObjectStorageException(SAFE_FAILURE_MESSAGE, exception);
        }
    }

    @Override
    public void delete(String objectKey) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(minioProperties.getBucket())
                    .object(objectKey)
                    .build());
        } catch (Exception exception) {
            throw new ObjectStorageException(SAFE_FAILURE_MESSAGE, exception);
        }
    }
}
