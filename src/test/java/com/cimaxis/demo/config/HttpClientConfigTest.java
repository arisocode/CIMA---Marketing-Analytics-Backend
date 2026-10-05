package com.cimaxis.demo.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class HttpClientConfigTest {

    @Test
    @DisplayName("Crea RestTemplate y ClientHttpRequestFactory con timeouts configurados")
    void creaRestTemplateConTimeoutsConfigurados() {
        HttpClientConfig config = new HttpClientConfig();
        ReflectionTestUtils.setField(config, "connectTimeoutSeconds", 3);
        ReflectionTestUtils.setField(config, "readTimeoutSeconds", 5);

        ClientHttpRequestFactory factory = config.clientHttpRequestFactory();
        assertThat(factory).isNotNull();

        RestTemplate restTemplate = config.restTemplate(factory);
        assertThat(restTemplate).isNotNull();
        assertThat(restTemplate.getRequestFactory()).isSameAs(factory);
    }
}
