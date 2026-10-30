package com.iskolar.server.catalog;

import com.iskolar.server.catalog.CatalogDtos.CatalogSnapshot;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Public catalog endpoints — the desktop app polls the version and pulls the
 * full snapshot when it changes.
 */
@RestController
@RequestMapping("/api/catalog")
public class CatalogController {

    private final CatalogService catalog;

    public CatalogController(CatalogService catalog) {
        this.catalog = catalog;
    }

    @GetMapping("/version")
    public Map<String, Integer> version() {
        return Map.of("version", catalog.version());
    }

    @GetMapping
    public CatalogSnapshot snapshot() {
        return catalog.snapshot();
    }
}
