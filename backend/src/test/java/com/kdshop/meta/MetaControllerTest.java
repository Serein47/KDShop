package com.kdshop.meta;

import com.kdshop.common.TraceIdFilter;
import com.kdshop.config.MetaProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MetaController.class)
@EnableConfigurationProperties(MetaProperties.class)
@Import(TraceIdFilter.class)
@TestPropertySource(properties = {
        "kdshop.meta.currency=CNY",
        "kdshop.meta.timezone=Asia/Shanghai",
        "kdshop.meta.shipping-free-threshold-cent=9900",
        "kdshop.meta.shipping-fee-cent=1000",
        "kdshop.meta.pay-timeout-minutes=15",
        "kdshop.meta.auto-confirm-days=15",
        "kdshop.meta.settlement-protection-days=7"
})
class MetaControllerTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void metaReturnsUnifiedResponseWithTraceId() throws Exception {
        mvc.perform(get("/api/v1/meta"))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Request-Id"))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("ok"))
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andExpect(jsonPath("$.data.currency").value("CNY"))
                .andExpect(jsonPath("$.data.shipping.freeThresholdCent").value(9900))
                .andExpect(jsonPath("$.data.shipping.feeCent").value(1000))
                .andExpect(jsonPath("$.data.order.payTimeoutMinutes").value(15))
                .andExpect(jsonPath("$.data.features.agent").value(false));
    }
}
