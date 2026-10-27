package org.praskanth.storage;

import lombok.RequiredArgsConstructor;
import org.praskanth.config.ProductImageProperties;
import org.praskanth.exception.BadRequestException;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Accepts only real JPEG / PNG / WebP pictures. The file's own first bytes ("magic numbers") decide; the file name and
 * the Content-Type header sent by the browser are ignored because both can be faked.
 */
@Component
@RequiredArgsConstructor
public class ImageValidator {

    public record DetectedImage(String contentType, String extension) {
    }

    private final ProductImageProperties properties;

    /** Checks the whole batch first, so nothing is written unless every picture is acceptable. */
    public List<DetectedImage> validateAll(List<? extends MultipartFile> files, int slotsLeft) {
        if (files.size() > slotsLeft) {
            throw new BadRequestException("A product can have at most " + properties.getMaxCount() + " pictures ("
                    + Math.max(slotsLeft, 0) + " more allowed, " + files.size() + " sent).");
        }
        List<DetectedImage> detected = new ArrayList<>(files.size());
        for (MultipartFile file : files) {
            detected.add(inspect(file));
        }
        return detected;
    }

    private DetectedImage inspect(MultipartFile file) {
        String label = file.getOriginalFilename() == null ? "picture" : "'" + file.getOriginalFilename() + "'";
        if (file.isEmpty()) {
            throw new BadRequestException("The file " + label + " is empty.");
        }
        long max = properties.getMaxSizeBytes();
        if (file.getSize() > max) {
            throw new BadRequestException("The file " + label + " is larger than " + (max / (1024 * 1024)) + " MB.");
        }
        byte[] head;
        try (InputStream in = file.getInputStream()) {
            head = in.readNBytes(12);
        } catch (IOException e) {
            throw new BadRequestException("The file " + label + " could not be read.");
        }
        if (startsWith(head, 0xFF, 0xD8, 0xFF)) {
            return new DetectedImage("image/jpeg", "jpg");
        }
        if (startsWith(head, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)) {
            return new DetectedImage("image/png", "png");
        }
        if (head.length >= 12 && startsWith(head, 'R', 'I', 'F', 'F')
                && head[8] == 'W' && head[9] == 'E' && head[10] == 'B' && head[11] == 'P') {
            return new DetectedImage("image/webp", "webp");
        }
        throw new BadRequestException("The file " + label + " is not a JPEG, PNG or WebP picture.");
    }

    private static boolean startsWith(byte[] data, int... expected) {
        if (data.length < expected.length) {
            return false;
        }
        for (int i = 0; i < expected.length; i++) {
            if ((data[i] & 0xFF) != expected[i]) {
                return false;
            }
        }
        return true;
    }
}
