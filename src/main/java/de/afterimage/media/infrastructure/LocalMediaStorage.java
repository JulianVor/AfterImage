package de.afterimage.media.infrastructure;

import de.afterimage.config.AfterimageProperties;
import de.afterimage.media.application.MediaStorage;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Component
public class LocalMediaStorage implements MediaStorage {

    private final Path root;

    public LocalMediaStorage(AfterimageProperties properties) throws IOException {
        this.root = properties.media().root().toAbsolutePath().normalize();
        Files.createDirectories(root);
    }

    @Override
    public String store(MultipartFile file) throws IOException {
        try (var input = file.getInputStream()) {
            return store(input, file.getOriginalFilename());
        }
    }

    @Override
    public String store(InputStream input, String originalFilename) throws IOException {
        String original = StringUtils.cleanPath(originalFilename == null ? "upload" : originalFilename);
        String extension = original.lastIndexOf('.') >= 0 ? original.substring(original.lastIndexOf('.')) : "";
        String storageKey = UUID.randomUUID() + extension.toLowerCase();
        Path target = resolve(storageKey);
        Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
        return storageKey;
    }

    @Override
    public Resource load(String storageKey) {
        try {
            return new UrlResource(resolve(storageKey).toUri());
        } catch (MalformedURLException exception) {
            throw new IllegalArgumentException("Invalid media key", exception);
        }
    }

    @Override
    public void delete(String storageKey) throws IOException {
        Files.deleteIfExists(resolve(storageKey));
    }

    private Path resolve(String storageKey) {
        Path target = root.resolve(storageKey).normalize();
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("Media key escapes storage root");
        }
        return target;
    }
}
