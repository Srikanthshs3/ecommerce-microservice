package org.praskanth.storage;

public record StoredImage(String fileName, String contentType, long sizeBytes) {
}
