package de.afterimage.web.publicsite;

import de.afterimage.piwigo.application.PiwigoGalleryService;
import de.afterimage.piwigo.application.PiwigoGateway;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/media/piwigo")
public class PiwigoMediaController {
    private final PiwigoGalleryService galleries;
    public PiwigoMediaController(PiwigoGalleryService galleries) { this.galleries = galleries; }

    @GetMapping("/albums/{albumId}/images")
    PiwigoGateway.ImagePage images(@PathVariable long albumId, @RequestParam(defaultValue = "0") int page) {
        try { return galleries.publicImages(albumId, page); }
        catch (IllegalArgumentException exception) { throw new ResponseStatusException(HttpStatus.NOT_FOUND); }
    }
}
