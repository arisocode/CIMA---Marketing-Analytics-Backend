package com.cimaxis.demo.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class JwksControllerTest {

    @Mock
    private ServiceJwtSigner signer;

    @InjectMocks
    private JwksController controller;

    @Test
    void returnsJwksDocument() {
        Map<String, Object> mockJwks = Map.of("keys", List.of(Map.of("kid", "key-1")));
        when(signer.getJwksDocument()).thenReturn(mockJwks);

        Map<String, Object> result = controller.getJwks();

        assertThat(result).isEqualTo(mockJwks);
    }
}
