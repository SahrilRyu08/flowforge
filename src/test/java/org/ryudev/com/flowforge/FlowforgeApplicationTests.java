package org.ryudev.com.flowforge;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(classes = FlowforgeApplication.class)
@ActiveProfiles("test")
class FlowforgeApplicationTests {

    @Test
    void contextLoads() {
    }

}
