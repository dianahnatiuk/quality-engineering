package lab2.config;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PricingClientConfigTest {

    @Test
    void readsKeyFromEnvironment() {
        Map<String, String> env = Map.of(PricingClientConfig.ENV_API_KEY, "value-from-environment");
        assertEquals("value-from-environment", PricingClientConfig.apiKey(env::get).orElseThrow());
    }

    @Test
    void missingOrBlankVariable_meansNoKey() {
        assertTrue(PricingClientConfig.apiKey(name -> null).isEmpty());
        assertTrue(PricingClientConfig.apiKey(name -> "   ").isEmpty());
    }
}
