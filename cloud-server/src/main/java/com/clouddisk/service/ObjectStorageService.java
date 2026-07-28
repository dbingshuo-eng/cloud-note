package com.clouddisk.service;

import java.io.InputStream;

public interface ObjectStorageService {

    void upload(String objectKey, InputStream inputStream, long size, String contentType);

    void stat(String objectKey);

    InputStream download(String objectKey);

    void delete(String objectKey);
}
