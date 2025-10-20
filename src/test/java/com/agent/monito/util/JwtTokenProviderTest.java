package com.agent.monito.util;

import com.agent.monito.global.jwt.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
    "jwt.secret=test-secret-key-for-unit-testing-1234567890"
})
class JwtTokenProviderTest {

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Test
    void generateAndPrintToken() {
        String token = jwtTokenProvider.createToken("central-backend", 3600_000L);
        System.out.println("🧪 Generated test token:\n" + token);
    }
}