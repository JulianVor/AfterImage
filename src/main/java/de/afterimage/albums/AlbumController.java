package de.afterimage.albums;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;

@Controller
public class AlbumController {

    private final AlbumCatalog catalog;

    public AlbumController(AlbumCatalog catalog) {
        this.catalog = catalog;
    }

    @GetMapping("/alben")
    String index(Model model) {
        model.addAttribute("albums", catalog.all());
        return "public/alben";
    }

    @GetMapping("/alben/{slug}")
    String show(@PathVariable String slug, Model model) {
        Album album = catalog.find(slug);
        if (album == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        model.addAttribute("album", album);
        model.addAttribute("previous", catalog.previous(album));
        model.addAttribute("next", catalog.next(album));
        return "public/album";
    }
}
