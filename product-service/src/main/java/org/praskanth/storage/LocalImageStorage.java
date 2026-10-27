package org.praskanth.storage;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.praskanth.config.ProductImageProperties;
import org.praskanth.exception.NotFoundException;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.regex.Pattern;

/** Keeps pictures as plain files in {@code product.images.storage-dir}. */
@Component
@RequiredArgsConstructor
@Slf4j
public class LocalImageStorage implements ImageStorage {

    /**
     * The only names that can exist: server-generated UUID, optional "_t" (thumbnail) and a whitelisted extension.
     * Anything else ("../etc/passwd", "a/b.jpg") is rejected before the disk is touched.
     */
    private static final Pattern SAFE_KEY =
            Pattern.compile("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}(_t)?\\.(jpg|png|webp)$");

    private final ProductImageProperties properties;
    private Path root;

    @PostConstruct
    void init() throws IOException {
        root = Paths.get(properties.getStorageDir()).toAbsolutePath().normalize();
        Files.createDirectories(root);
        log.info("Product pictures are stored in {}", root);
    }

    @Override
    public void put(String key, byte[] data) {
        requireSafe(key);
        Path target = root.resolve(key);
        Path temp = root.resolve(key + ".part");
        try {
            Files.write(temp, data);
            // write-then-rename: a reader never sees a half-written picture
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            try {
                Files.deleteIfExists(temp);
            } catch (IOException ignored) {
                // nothing more we can do
            }
            throw new UncheckedIOException("Could not store product picture", e);
        }
    }

    @Override
    public Resource get(String key) {
        if (key == null || !SAFE_KEY.matcher(key).matches()) {
            throw new NotFoundException("Picture not found");
        }
        Path path = root.resolve(key).normalize();
        if (!path.startsWith(root) || !Files.isRegularFile(path)) {
            throw new NotFoundException("Picture not found");
        }
        return new FileSystemResource(path);
    }

    @Override
    public void delete(String key) {
        if (key == null || !SAFE_KEY.matcher(key).matches()) {
            return;
        }
        try {
            Files.deleteIfExists(root.resolve(key).normalize());
        } catch (IOException e) {
            log.warn("Could not delete product picture {}: {}", key, e.getMessage());
        }
    }

    private static void requireSafe(String key) {
        if (key == null || !SAFE_KEY.matcher(key).matches()) {
            throw new IllegalArgumentException("Unsafe picture name: " + key);
        }
    }
}
