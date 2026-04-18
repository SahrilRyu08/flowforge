package org.ryudev.com.flowforge.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class RateLimitFilterTest {

    @Autowired
    MockMvc mvc;

    @Test
    void return429AfterLimitExceeded() throws Exception {
        for (int i = 0; i < 11; i++) {
            ResultActions perform = mvc.perform(get("/api/workflows"));
            if (i == 10) {
                perform.andExpect(status().isTooManyRequests());
            }
        }
    }
}