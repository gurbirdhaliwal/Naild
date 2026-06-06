package com.knappi.security.media;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourceRegion;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRange;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/media")
public class MediaController {

    private static final long CHUNK_SIZE_BYTES = 1024 * 1024;
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            ".mp4",
            ".webm",
            ".mov",
            ".m4v",
            ".jpg",
            ".jpeg",
            ".png",
            ".webp",
            ".gif"
    );

    private final Path mediaRoot;

    public MediaController(
            @Value("${app.media.root:../Naild-WebApp/naild-website/public}") String mediaRoot
    ) {
        this.mediaRoot = Path.of(mediaRoot).toAbsolutePath().normalize();
    }

    @GetMapping("/**")
    public ResponseEntity<?> streamMedia(
            HttpServletRequest request,
            @RequestHeader HttpHeaders headers
    ) throws IOException {
        Resource resource = resolveResource(request);
        long contentLength = resource.contentLength();
        MediaType mediaType = MediaTypeFactory.getMediaType(resource)
                .orElse(MediaType.APPLICATION_OCTET_STREAM);

        List<HttpRange> ranges;
        try {
            ranges = headers.getRange();
        } catch (IllegalArgumentException ex) {
            return rangeNotSatisfiable(contentLength);
        }

        if (ranges.isEmpty()) {
            return ResponseEntity.ok()
                    .contentType(mediaType)
                    .contentLength(contentLength)
                    .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic())
                    .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                    .body(resource);
        }

        if (ranges.size() > 1) {
            return rangeNotSatisfiable(contentLength);
        }

        HttpRange range = ranges.get(0);
        long start = range.getRangeStart(contentLength);
        long requestedEnd = range.getRangeEnd(contentLength);
        long end = Math.min(requestedEnd, start + CHUNK_SIZE_BYTES - 1);

        if (start >= contentLength || end < start) {
            return rangeNotSatisfiable(contentLength);
        }

        ResourceRegion region = new ResourceRegion(resource, start, end - start + 1);

        return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                .contentType(mediaType)
                .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic())
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .header(HttpHeaders.CONTENT_RANGE, "bytes " + start + "-" + end + "/" + contentLength)
                .body(region);
    }

    private Resource resolveResource(HttpServletRequest request) {
        String prefix = request.getContextPath() + "/api/v1/media/";
        String uri = request.getRequestURI();

        if (!uri.startsWith(prefix)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Media asset not found.");
        }

        String rawPath = URLDecoder.decode(uri.substring(prefix.length()), StandardCharsets.UTF_8);
        Path requestedPath = mediaRoot.resolve(rawPath).normalize();

        if (!requestedPath.startsWith(mediaRoot) || !requestedPath.toFile().isFile()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Media asset not found.");
        }

        String fileName = requestedPath.getFileName().toString().toLowerCase();
        boolean allowed = ALLOWED_EXTENSIONS.stream().anyMatch(fileName::endsWith);
        if (!allowed) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Unsupported media type.");
        }

        return new FileSystemResource(requestedPath);
    }

    private ResponseEntity<ResourceRegion> rangeNotSatisfiable(long contentLength) {
        return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .header(HttpHeaders.CONTENT_RANGE, "bytes */" + contentLength)
                .build();
    }
}
