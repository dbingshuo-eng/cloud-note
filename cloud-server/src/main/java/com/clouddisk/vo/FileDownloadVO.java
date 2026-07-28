package com.clouddisk.vo;

import java.io.InputStream;
import java.util.function.Supplier;

public record FileDownloadVO(
        String fileName,
        long contentLength,
        Supplier<InputStream> inputStreamSupplier
) {

    public InputStream inputStream() {
        return inputStreamSupplier.get();
    }

    @Override
    public String toString() {
        return "FileDownloadVO[fileName=%s, contentLength=%d]".formatted(fileName, contentLength);
    }
}
