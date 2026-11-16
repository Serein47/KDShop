package com.kdshop.media;

import com.kdshop.common.ApiResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/** 顾客侧图片上传（售后凭证等），登录即可，前缀由前端指定。 */
@RestController
@RequestMapping("/api/v1/uploads")
public class UploadController {

    private final MediaService mediaService;

    public UploadController(MediaService mediaService) {
        this.mediaService = mediaService;
    }

    @PostMapping("/image")
    public ApiResponse<Map<String, String>> upload(@RequestParam("file") MultipartFile file,
                                                   @RequestParam(value = "prefix", defaultValue = "after-sales") String prefix) {
        return ApiResponse.ok(Map.of("key", mediaService.upload(file, prefix)));
    }
}
