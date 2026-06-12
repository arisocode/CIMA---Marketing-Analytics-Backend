package com.cimaxis.demo.gateway;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/_gateway")
public class GatewayManifestController {

    private static final String MANIFEST_PATH = "gateway/gateway.manifest.json";

    @GetMapping(value = "/gateway.manifest.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Resource> getManifest() {
        Resource manifest = new ClassPathResource(MANIFEST_PATH);
        if (!manifest.exists()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Gateway manifest not found");
        }

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .cacheControl(CacheControl.noCache())
                .body(manifest);
    }
}
