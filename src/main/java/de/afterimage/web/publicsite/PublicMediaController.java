package de.afterimage.web.publicsite;

import de.afterimage.media.application.PublicMediaService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRange;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@RestController
public class PublicMediaController {

    private final PublicMediaService media;

    public PublicMediaController(PublicMediaService media) {
        this.media = media;
    }

    // Written by hand against HttpServletResponse rather than returning ResponseEntity<ResourceRegion> -
    // this Spring version has no HttpMessageConverter registered for ResourceRegion, which turned every
    // request (Chrome sends a Range header even for a plain <audio>/<video> load) into a 500.
    @GetMapping("/media/{id}")
    void media(@PathVariable UUID id, @RequestHeader(value = "Range", required = false) String range,
               HttpServletResponse response) throws IOException {
        PublicMediaService.PublicMedia item = media.load(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        MediaType type = item.mimeType() == null ? MediaType.APPLICATION_OCTET_STREAM
                : MediaType.parseMediaType(item.mimeType());
        long contentLength = item.resource().contentLength();

        response.setContentType(type.toString());
        response.setHeader(HttpHeaders.CACHE_CONTROL, CacheControl.maxAge(30, TimeUnit.DAYS).cachePublic().immutable().getHeaderValue());
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader(HttpHeaders.ACCEPT_RANGES, "bytes");

        long start = 0;
        long end = contentLength - 1;
        if (range != null) {
            List<HttpRange> ranges;
            try {
                ranges = HttpRange.parseRanges(range);
            } catch (IllegalArgumentException exception) {
                response.setStatus(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE.value());
                response.setHeader(HttpHeaders.CONTENT_RANGE, "bytes */" + contentLength);
                return;
            }
            if (!ranges.isEmpty()) {
                HttpRange requested = ranges.get(0);
                start = requested.getRangeStart(contentLength);
                end = requested.getRangeEnd(contentLength);
            }
        }
        long rangeLength = end - start + 1;
        response.setContentLengthLong(rangeLength);
        if (range != null) {
            response.setStatus(HttpStatus.PARTIAL_CONTENT.value());
            response.setHeader(HttpHeaders.CONTENT_RANGE, "bytes " + start + "-" + end + "/" + contentLength);
        } else {
            response.setStatus(HttpStatus.OK.value());
        }

        try (InputStream input = item.resource().getInputStream()) {
            input.skipNBytes(start);
            OutputStream output = response.getOutputStream();
            byte[] buffer = new byte[8192];
            long remaining = rangeLength;
            while (remaining > 0) {
                int read = input.read(buffer, 0, (int) Math.min(buffer.length, remaining));
                if (read < 0) break;
                output.write(buffer, 0, read);
                remaining -= read;
            }
        } catch (IOException clientDisconnected) {
            // The <audio>/<video> element routinely opens a request and cancels it once it knows the
            // range it actually wants - that's a client-side abort, not a server error, so swallow it
            // rather than letting Spring turn it into a 500.
        }
    }
}
