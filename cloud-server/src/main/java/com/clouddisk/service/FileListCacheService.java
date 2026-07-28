package com.clouddisk.service;

import com.clouddisk.vo.FileInfoVO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
public class FileListCacheService {

    private static final Logger LOGGER = LoggerFactory.getLogger(FileListCacheService.class);
    private static final int SCHEMA_VERSION = 2;
    private static final Duration MAX_TTL = Duration.ofHours(1);
    private static final Set<String> SORT_COLUMNS = Set.of(
            "file_name",
            "file_size",
            "file_type",
            "create_time",
            "update_time"
    );
    private static final Set<String> SORT_ORDERS = Set.of("ASC", "DESC");

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Duration ttl;

    @Autowired
    public FileListCacheService(
            ObjectProvider<StringRedisTemplate> redisTemplateProvider,
            ObjectMapper objectMapper,
            @Value("${cloud.cache.file-list-ttl:PT5M}") Duration ttl
    ) {
        this(redisTemplateProvider.getIfAvailable(), objectMapper, ttl);
    }

    FileListCacheService(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            Duration ttl
    ) {
        if (ttl == null || ttl.isZero() || ttl.isNegative() || ttl.compareTo(MAX_TTL) > 0) {
            throw new IllegalArgumentException("file list cache TTL must be between 1 nanosecond and 1 hour");
        }
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper.copy();
        this.ttl = ttl;
    }

    public Optional<List<FileInfoVO>> get(
            Long userId,
            Long parentId,
            String sortColumn,
            String sortOrder
    ) {
        return getWithGeneration(userId, parentId, sortColumn, sortOrder).files();
    }

    public CacheRead getWithGeneration(
            Long userId,
            Long parentId,
            String sortColumn,
            String sortOrder
    ) {
        String key = listKey(userId, parentId);
        String variant = variantKey(sortColumn, sortOrder);
        if (redisTemplate == null || variant == null) {
            logMiss(key, variant, "disabled_or_unsupported");
            return new CacheRead(Optional.empty(), null);
        }

        Long generation = readGeneration(userId);
        if (generation == null) {
            logMiss(key, variant, "generation_unavailable");
            return new CacheRead(Optional.empty(), null);
        }
        try {
            String json = redisTemplate.opsForValue().get(key);
            if (json == null) {
                logMiss(key, variant, "key_absent");
                return new CacheRead(Optional.empty(), generation);
            }

            CachePayload payload = objectMapper.readValue(json, CachePayload.class);
            if (!validPayload(payload, generation)) {
                logMiss(key, variant, "incompatible_payload");
                safeDelete(key);
                return new CacheRead(Optional.empty(), generation);
            }

            List<FileInfoVO> files = payload.variants().get(variant);
            if (files == null) {
                logMiss(key, variant, "variant_absent");
                return new CacheRead(Optional.empty(), generation);
            }

            Long confirmedGeneration = readGeneration(userId);
            if (!generation.equals(confirmedGeneration)) {
                logMiss(key, variant, "generation_changed_during_read");
                safeDelete(key);
                return new CacheRead(Optional.empty(), confirmedGeneration);
            }

            LOGGER.debug("file_list_cache event=hit key={} variant={}", key, variant);
            return new CacheRead(Optional.of(List.copyOf(files)), generation);
        } catch (JsonProcessingException exception) {
            LOGGER.warn(
                    "file_list_cache event=parse_failed key={} exception={}",
                    key,
                    exception.getClass().getSimpleName()
            );
            safeDelete(key);
            return new CacheRead(Optional.empty(), generation);
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "file_list_cache event=read_failed key={} exception={}",
                    key,
                    exception.getClass().getSimpleName()
            );
            return new CacheRead(Optional.empty(), generation);
        }
    }

    public void put(
            Long userId,
            Long parentId,
            String sortColumn,
            String sortOrder,
            List<FileInfoVO> files
    ) {
        if (variantKey(sortColumn, sortOrder) == null) {
            return;
        }
        putIfGenerationUnchanged(
                userId,
                parentId,
                sortColumn,
                sortOrder,
                readGeneration(userId),
                files
        );
    }

    public void putIfGenerationUnchanged(
            Long userId,
            Long parentId,
            String sortColumn,
            String sortOrder,
            Long expectedGeneration,
            List<FileInfoVO> files
    ) {
        String variant = variantKey(sortColumn, sortOrder);
        if (redisTemplate == null
                || variant == null
                || expectedGeneration == null
                || files == null) {
            return;
        }

        String key = listKey(userId, parentId);
        try {
            Long currentGeneration = readGeneration(userId);
            if (!expectedGeneration.equals(currentGeneration)) {
                logMiss(key, variant, "generation_changed_before_write");
                return;
            }
            Map<String, List<FileInfoVO>> variants =
                    readExistingVariants(key, expectedGeneration);
            variants.put(variant, List.copyOf(files));
            CachePayload payload = new CachePayload(
                    SCHEMA_VERSION,
                    expectedGeneration,
                    Collections.unmodifiableMap(new LinkedHashMap<>(variants))
            );
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(payload), ttl);
        } catch (JsonProcessingException exception) {
            LOGGER.warn(
                    "file_list_cache event=serialize_failed key={} exception={}",
                    key,
                    exception.getClass().getSimpleName()
            );
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "file_list_cache event=write_failed key={} exception={}",
                    key,
                    exception.getClass().getSimpleName()
            );
        }
    }

    public void advanceGeneration(Long userId) {
        if (redisTemplate == null) {
            return;
        }
        String key = generationKey(userId);
        try {
            redisTemplate.opsForValue().increment(key);
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "file_list_cache event=generation_increment_failed key={} exception={}",
                    key,
                    exception.getClass().getSimpleName()
            );
        }
    }

    public void invalidateList(Long userId, Long parentId) {
        safeDelete(listKey(userId, parentId));
    }

    public void invalidateRecycleBin(Long userId) {
        safeDelete(recycleKey(userId));
    }

    static String listKey(Long userId, Long parentId) {
        return "file:list:%d:%d".formatted(userId, parentId);
    }

    static String generationKey(Long userId) {
        return "file:list:generation:%d".formatted(userId);
    }

    private static String recycleKey(Long userId) {
        return "file:recycle:%d".formatted(userId);
    }

    private Map<String, List<FileInfoVO>> readExistingVariants(String key, Long generation)
            throws JsonProcessingException {
        String json = redisTemplate.opsForValue().get(key);
        if (json == null) {
            return new LinkedHashMap<>();
        }

        CachePayload payload = objectMapper.readValue(json, CachePayload.class);
        if (!validPayload(payload, generation)) {
            return new LinkedHashMap<>();
        }

        Map<String, List<FileInfoVO>> variants = new LinkedHashMap<>();
        payload.variants().forEach((variant, files) -> {
            if (supportedVariant(variant) && files != null) {
                variants.put(variant, List.copyOf(files));
            }
        });
        return variants;
    }

    private boolean validPayload(CachePayload payload, Long generation) {
        return payload != null
                && payload.schemaVersion() == SCHEMA_VERSION
                && generation.equals(payload.generation())
                && payload.variants() != null;
    }

    private Long readGeneration(Long userId) {
        if (redisTemplate == null) {
            return null;
        }
        String key = generationKey(userId);
        try {
            String value = redisTemplate.opsForValue().get(key);
            if (value == null) {
                return 0L;
            }
            long generation = Long.parseLong(value);
            if (generation < 0) {
                throw new NumberFormatException("negative generation");
            }
            return generation;
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "file_list_cache event=generation_read_failed key={} exception={}",
                    key,
                    exception.getClass().getSimpleName()
            );
            return null;
        }
    }

    private static String variantKey(String sortColumn, String sortOrder) {
        if (!SORT_COLUMNS.contains(sortColumn) || !SORT_ORDERS.contains(sortOrder)) {
            return null;
        }
        return sortColumn + ":" + sortOrder;
    }

    private static boolean supportedVariant(String variant) {
        int separator = variant == null ? -1 : variant.lastIndexOf(':');
        return separator > 0
                && variantKey(variant.substring(0, separator), variant.substring(separator + 1)) != null;
    }

    private void safeDelete(String key) {
        if (redisTemplate == null) {
            return;
        }
        try {
            redisTemplate.delete(key);
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "file_list_cache event=invalidation_failed key={} exception={}",
                    key,
                    exception.getClass().getSimpleName()
            );
        }
    }

    private void logMiss(String key, String variant, String reason) {
        LOGGER.debug(
                "file_list_cache event=miss key={} variant={} reason={}",
                key,
                variant,
                reason
        );
    }

    private record CachePayload(
            int schemaVersion,
            long generation,
            Map<String, List<FileInfoVO>> variants
    ) {
    }

    public record CacheRead(Optional<List<FileInfoVO>> files, Long generation) {
        public CacheRead {
            files = files == null ? Optional.empty() : files;
        }
    }
}
