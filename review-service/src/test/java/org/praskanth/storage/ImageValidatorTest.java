package org.praskanth.storage;

import org.junit.jupiter.api.Test;
import org.praskanth.config.ReviewProperties;
import org.praskanth.exception.BadRequestException;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ImageValidatorTest {

    private final ReviewProperties properties = new ReviewProperties();
    private final ImageValidator validator = new ImageValidator(properties);

    private static MockMultipartFile file(String name, byte[] bytes) {
        return new MockMultipartFile("images", name, "application/octet-stream", bytes);
    }

    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0, 0, 0, 0, 0, 0, 0};
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
    private static final byte[] WEBP = {'R', 'I', 'F', 'F', 1, 2, 3, 4, 'W', 'E', 'B', 'P'};

    @Test
    void acceptsJpegPngAndWebpByContentNotByName() {
        var result = validator.validateAll(List.of(file("a.txt", JPEG), file("b.exe", PNG), file("c", WEBP)), 5);
        assertEquals(List.of("jpg", "png", "webp"), result.stream().map(ImageValidator.DetectedImage::extension).toList());
    }

    @Test
    void rejectsNonImagesEvenWhenNamedLikeImages() {
        var fake = file("evil.jpg", "<?php echo 1; ?>".getBytes());
        assertThrows(BadRequestException.class, () -> validator.validateAll(List.of(fake), 5));
    }

    @Test
    void rejectsEmptyFile() {
        assertThrows(BadRequestException.class, () -> validator.validateAll(List.of(file("a.jpg", new byte[0])), 5));
    }

    @Test
    void rejectsTooManyPictures() {
        var files = List.of(file("1.jpg", JPEG), file("2.jpg", JPEG), file("3.jpg", JPEG));
        assertThrows(BadRequestException.class, () -> validator.validateAll(files, 2));
    }

    @Test
    void rejectsOversizedPicture() {
        properties.getImages().setMaxSizeBytes(8);
        assertThrows(BadRequestException.class, () -> validator.validateAll(List.of(file("a.jpg", JPEG)), 5));
    }
}
