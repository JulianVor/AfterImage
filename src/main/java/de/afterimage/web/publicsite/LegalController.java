package de.afterimage.web.publicsite;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
class LegalController {

    @GetMapping("/impressum")
    String imprint() {
        return "public/impressum";
    }

    @GetMapping("/datenschutz")
    String privacy() {
        return "public/datenschutz";
    }
}
