package de.afterimage.media.application;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;

public interface MediaStorage {
    String store(MultipartFile file) throws IOException;
    String store(InputStream input, String originalFilename) throws IOException;
    Resource load(String storageKey);
    void delete(String storageKey) throws IOException;
}
