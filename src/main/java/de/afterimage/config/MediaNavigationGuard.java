package de.afterimage.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Raises the bar for saving images: a browser loading an image into a page sends Sec-Fetch-Dest "image", while
 * opening the file directly (open image in new tab, pasting the address) is a "navigate" request. Those direct
 * requests get a 404. Clients that send no Sec-Fetch headers (crawlers, link previews, curl) are not affected, so
 * this is a hurdle, not protection: anyone can still read the files from a page's network traffic.
 */
public class MediaNavigationGuard extends OncePerRequestFilter {

    private static final List<String> PROTECTED_PREFIXES =
            List.of("/img/", "/media/", "/regie-media/", "/api/media/piwigo/");
    private static final Set<String> DIRECT_DESTINATIONS = Set.of("document", "iframe", "frame", "embed", "object");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (isProtectedPath(request.getRequestURI()) && isDirectOpen(request) && !isAdmin()) {
            response.setHeader("Cache-Control", "no-store");
            response.setHeader("Vary", "Sec-Fetch-Dest, Sec-Fetch-Mode");
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        chain.doFilter(request, response);
    }

    private static boolean isProtectedPath(String path) {
        return PROTECTED_PREFIXES.stream().anyMatch(path::startsWith);
    }

    private static boolean isDirectOpen(HttpServletRequest request) {
        String mode = request.getHeader("Sec-Fetch-Mode");
        String destination = request.getHeader("Sec-Fetch-Dest");
        return "navigate".equalsIgnoreCase(mode)
                || (destination != null && DIRECT_DESTINATIONS.contains(destination.toLowerCase(Locale.ROOT)));
    }

    private static boolean isAdmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.isAuthenticated()
                && authentication.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    }
}
