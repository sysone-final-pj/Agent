package com.agent.monito.util;

import com.agent.monito.global.jwt.JwtTokenProvider;
import org.junit.jupiter.api.Test;

class JwtTokenProviderTest {

    @Test
    void generateAndPrintToken() {
        JwtTokenProvider jwtTokenProvider = new JwtTokenProvider();
        String token = jwtTokenProvider.createToken("central-backend", 3600_000L);
        System.out.println("🧪 Generated test token:\n" + token);
    }
}