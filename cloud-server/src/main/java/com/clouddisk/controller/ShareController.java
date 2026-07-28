package com.clouddisk.controller;

import com.clouddisk.common.ApiResponse;
import com.clouddisk.dto.ShareCreateRequest;
import com.clouddisk.dto.ShareVerifyRequest;
import com.clouddisk.service.ShareService;
import com.clouddisk.vo.ShareAccessVO;
import com.clouddisk.vo.ShareCreateVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/share")
public class ShareController {

    private final ShareService shareService;

    public ShareController(ShareService shareService) {
        this.shareService = shareService;
    }

    @PostMapping("/create")
    public ApiResponse<ShareCreateVO> create(@Valid @RequestBody ShareCreateRequest request) {
        return ApiResponse.success(shareService.create(request));
    }

    @GetMapping("/{code}")
    public ApiResponse<ShareAccessVO> get(@PathVariable("code") String code) {
        return ApiResponse.success(shareService.get(code));
    }

    @PostMapping("/{code}/verify")
    public ApiResponse<ShareAccessVO> verify(@PathVariable("code") String code,
                                             @Valid @RequestBody ShareVerifyRequest request) {
        return ApiResponse.success(shareService.verify(code, request));
    }
}
