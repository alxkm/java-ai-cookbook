package com.example.cookbook;

import org.springframework.core.io.Resource;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;

import java.io.IOException;
import java.io.InputStream;

/**
 * The mime type of an image, read off its first bytes rather than assumed.
 *
 * The type travels with the image and the provider takes it at its word. Hardcoding IMAGE_PNG
 * means the first JPEG someone drops in is sent labelled as a PNG, and the failure then surfaces
 * at the provider, a long way from where the file was chosen.
 */
final class ImageTypes {

    private static final MimeType WEBP = MimeType.valueOf("image/webp");

    private ImageTypes() {
    }

    static MimeType of(Resource image) throws IOException {
        try (InputStream in = image.getInputStream()) {
            return of(in.readNBytes(12));
        }
    }

    static MimeType of(byte[] bytes) {
        if (startsWith(bytes, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)) {
            return MimeTypeUtils.IMAGE_PNG;
        }
        if (startsWith(bytes, 0xFF, 0xD8, 0xFF)) {
            return MimeTypeUtils.IMAGE_JPEG;
        }
        if (startsWith(bytes, 'G', 'I', 'F', '8')) {
            return MimeTypeUtils.IMAGE_GIF;
        }
        if (startsWith(bytes, 'R', 'I', 'F', 'F') && bytes.length >= 12
                && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P') {
            return WEBP;
        }
        // Refuse rather than guess. A clear error here beats a confident wrong description later.
        throw new IllegalArgumentException("not a PNG, JPEG, GIF or WebP image");
    }

    private static boolean startsWith(byte[] bytes, int... signature) {
        if (bytes.length < signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if ((bytes[i] & 0xFF) != signature[i]) {
                return false;
            }
        }
        return true;
    }
}
