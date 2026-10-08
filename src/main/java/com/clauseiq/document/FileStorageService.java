package com.clauseiq.document;

import com.clauseiq.config.ClauseIqProperties;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * Local filesystem storage for the MVP. Files are stored under uploadDir/tenant-{id}/ with a random
 * name, so user-supplied filenames never become filesystem paths.
 */
@Service
public class FileStorageService {

    private final Path root;

    public FileStorageService(ClauseIqProperties properties) {
        this.root = Path.of(properties.storage().uploadDir()).toAbsolutePath().normalize();
    }

    public String store(Long tenantId, String extension, InputStream content) {
        try {
            Path dir = root.resolve("tenant-" + tenantId);
            Files.createDirectories(dir);
            Path target = dir.resolve(UUID.randomUUID() + "." + extension);
            Files.copy(content, target, StandardCopyOption.REPLACE_EXISTING);
            return root.relativize(target).toString();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to store file", e);
        }
    }

    public Path resolve(String storagePath) {
        Path path = root.resolve(storagePath).normalize();
        if (!path.startsWith(root)) {
            throw new IllegalArgumentException("Invalid storage path");
        }
        return path;
    }

    public void delete(String storagePath) {
        try {
            Files.deleteIfExists(resolve(storagePath));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to delete file", e);
        }
    }
}
