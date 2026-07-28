package com.clouddisk.service;

import com.clouddisk.common.ApiResponse;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class HealthService {

    public ApiResponse<Map<String, String>> health() {
        return ApiResponse.success(Map.of("status", "UP"));
    }
}
