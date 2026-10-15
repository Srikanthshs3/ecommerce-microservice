package org.praskanth.storage;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.praskanth.config.ReviewProperties;
import org.praskanth.exception.NotFoundException;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import java.util.regex.Pattern;

/** Stores pictures as plain files in {@code review.images.storage-dir}. */
@Component
@RequiredArgsConstructor
@Slf4j
public class LocalImageStorage implements ImageStorage {

    /** The only file names that can exist: server-generated UUID + a whitelisted extension. Blocks "../" tricks outright. */
    private static final Pattern SAFE_NAME =
            Pattern.compile("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(jpg|png|webp)$");

    private final ReviewProperties properties;
    private Path root;

    @PostConstruct
    void init() throws IOException {
        root = Paths.get(properties.getImages().getStorageDir()).toAbsolutePath().normalize();
        Files.createDirectories(root);
        log.info("Review pictures are stored in {}", root);
    }

    @Override
    public StoredImage store(MultipartFile file, String extension, String contentType) {
        String fileName = UUID.randomUUID() + "." + extension;
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, root.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not store review picture", e);
        }
        return new StoredImage(fileName, contentType, file.getSize());
    }

    @Override
    public Resource load(String fileName) {
        if (fileName == null || !SAFE_NAME.matcher(fileName).matches()) {
            throw new NotFoundException("Picture not found");
        }
        Path path = root.resolve(fileName).normalize();
        if (!path.startsWith(root) || !Files.isRegularFile(path)) {
            throw new NotFoundException("Picture not found");
        }
        return new FileSystemResource(path);
    }

    @Override
    public void delete(String fileName) {
        if (fileName == null || !SAFE_NAME.matcher(fileName).matches()) {
            return;
        }
        try {
            Files.deleteIfExists(root.resolve(fileName).normalize());
        } catch (IOException e) {
            log.warn("Could not delete review picture {}: {}", fileName, e.getMessage());
        }
    }
}
