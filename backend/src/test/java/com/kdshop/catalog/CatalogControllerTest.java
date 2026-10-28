package com.kdshop.catalog;

import com.kdshop.common.BusinessException;
import com.kdshop.common.ErrorCode;
import com.kdshop.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CatalogController.class)
@Import(SecurityConfig.class)
class CatalogControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private CatalogService catalogService;

    @Test
    void categoriesArePublic() throws Exception {
        when(catalogService.categories()).thenReturn(List.of());

        mvc.perform(get("/api/v1/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void missingProductReturnsUnified404() throws Exception {
        when(catalogService.detail(any())).thenThrow(new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));

        mvc.perform(get("/api/v1/products/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40001));
    }
}
