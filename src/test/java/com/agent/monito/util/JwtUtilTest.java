package com.agent.monito.util;

import org.junit.jupiter.api.Test;

class JwtUtilTest {

    @Test
    void generateAndPrintToken() {
        JwtUtil jwtUtil = new JwtUtil();
        String token = jwtUtil.createToken("central-backend", 3600_000L);
        System.out.println("🧪 Generated test token:\n" + token);
    }
}