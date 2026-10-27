package org.praskanth.storage;

import org.springframework.core.io.Resource;

/**
 * Where product pictures physically live. The rest of product-service only talks to this interface.
 *
 * Default: {@link LocalImageStorage} (a folder, mounted as a Docker volume).
 * To move to S3 / MinIO / Cloudinary later, write ONE more class that implements these three methods and make it the
 * only bean - nothing else in the service changes, and the URLs stored in MongoDB stay valid because they point at
 * this service, not at the storage.
 */
public interface ImageStorage {

    /** Writes (or replaces) the object stored under {@code key}. */
    void put(String key, byte[] data);

    /** @throws org.praskanth.exception.NotFoundException when there is no such object */
    Resource get(String key);

    /** Best effort, never throws: a leftover file is harmless, a failed request is not. */
    void delete(String key);
}
