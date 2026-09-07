package de.afterimage.web.publicsite;

import de.afterimage.media.application.PublicMediaService;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@RestController
public class PublicMediaController {

    private final PublicMediaService media;

    public PublicMediaController(PublicMediaService media) {
        this.media = media;
    }

    @GetMapping("/media/{id}")
    ResponseEntity<?> media(@PathVariable UUID id) {
        PublicMediaService.PublicMedia item = media.load(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        MediaType type = item.mimeType() == null ? MediaType.APPLICATION_OCTET_STREAM
                : MediaType.parseMediaType(item.mimeType());
        return ResponseEntity.ok()
                .contentType(type)
                .cacheControl(CacheControl.maxAge(30, TimeUnit.DAYS).cachePublic().immutable())
                .header("X-Content-Type-Options", "nosniff")
                .body(item.resource());
    }
}
