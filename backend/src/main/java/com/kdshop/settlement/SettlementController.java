package com.kdshop.settlement;

import com.kdshop.common.ApiResponse;
import com.kdshop.settlement.dto.GenerateRequest;
import com.kdshop.settlement.dto.SettlementView;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class SettlementController {

    private final SettlementService settlementService;

    public SettlementController(SettlementService settlementService) {
        this.settlementService = settlementService;
    }

    @GetMapping("/admin/settlements")
    public ApiResponse<List<SettlementView>> listAll() {
        return ApiResponse.ok(settlementService.listAll());
    }

    @PostMapping("/admin/settlements/generate")
    public ApiResponse<List<SettlementView>> generate(@Valid @RequestBody GenerateRequest request) {
        return ApiResponse.ok(settlementService.generate(request.periodStart(), request.periodEnd()));
    }

    @PostMapping("/admin/settlements/{settlementNo}/confirm")
    public ApiResponse<SettlementView> confirm(@PathVariable String settlementNo) {
        return ApiResponse.ok(settlementService.confirm(settlementNo));
    }

    @GetMapping("/merchant/settlements")
    public ApiResponse<List<SettlementView>> mine() {
        return ApiResponse.ok(settlementService.listMine());
    }
}
