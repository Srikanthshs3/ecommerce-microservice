package org.praskanth.storage;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

/**
 * Where review pictures are kept. The default implementation writes to a local folder (a Docker volume in
 * docker-compose). To use S3 / MinIO / Cloudinary instead, add another implementation of this interface and make it
 * the only bean - the rest of the service does not change.
 */
public interface ImageStorage {

    /** Saves the (already validated) file under a new random name. */
    StoredImage store(MultipartFile file, String extension, String contentType);

    /** @throws org.praskanth.exception.NotFoundException when there is no such picture */
    Resource load(String fileName);

    /** Best effort, never throws: a leftover file is harmless, a failed review request is not. */
    void delete(String fileName);
}
