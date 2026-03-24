package io.github.hectorvent.floci.services.cloudfront;

import com.fasterxml.jackson.core.type.TypeReference;
import io.github.hectorvent.floci.core.common.AwsException;
import io.github.hectorvent.floci.core.common.RegionResolver;
import io.github.hectorvent.floci.core.storage.StorageBackend;
import io.github.hectorvent.floci.core.storage.StorageFactory;
import io.github.hectorvent.floci.services.cloudfront.model.CacheBehavior;
import io.github.hectorvent.floci.services.cloudfront.model.CachePolicy;
import io.github.hectorvent.floci.services.cloudfront.model.Distribution;
import io.github.hectorvent.floci.services.cloudfront.model.Invalidation;
import io.github.hectorvent.floci.services.cloudfront.model.Origin;
import io.github.hectorvent.floci.services.cloudfront.model.OriginAccessControl;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class CloudFrontService {

    private final StorageBackend<String, Distribution> distributionStore;
    private final StorageBackend<String, Invalidation> invalidationStore;
    private final StorageBackend<String, CachePolicy> cachePolicyStore;
    private final StorageBackend<String, OriginAccessControl> oacStore;
    private final RegionResolver regionResolver;

    @Inject
    public CloudFrontService(StorageFactory storageFactory, RegionResolver regionResolver) {
        this.distributionStore = storageFactory.create("cloudfront", "cloudfront-distributions.json",
                new TypeReference<>() {});
        this.invalidationStore = storageFactory.create("cloudfront", "cloudfront-invalidations.json",
                new TypeReference<>() {});
        this.cachePolicyStore = storageFactory.create("cloudfront", "cloudfront-cache-policies.json",
                new TypeReference<>() {});
        this.oacStore = storageFactory.create("cloudfront", "cloudfront-origin-access-controls.json",
                new TypeReference<>() {});
        this.regionResolver = regionResolver;
    }

    // ─────────────── Distributions ───────────────

    public Distribution createDistribution(String callerReference, String comment, boolean enabled,
                                           List<Origin> origins, String defaultRootObject,
                                           CacheBehavior defaultCacheBehavior,
                                           List<CacheBehavior> cacheBehaviors) {
        boolean duplicate = distributionStore.scan(k -> true).stream()
                .anyMatch(d -> callerReference.equals(d.getCallerReference()));
        if (duplicate) {
            throw new AwsException("DistributionAlreadyExists",
                    "The caller reference is already used by another distribution.", 409);
        }

        String id = UUID.randomUUID().toString().replace("-", "").substring(0, 14).toUpperCase();
        String accountId = regionResolver.getAccountId();
        long now = Instant.now().getEpochSecond();

        Distribution dist = new Distribution();
        dist.setId(id);
        dist.setArn("arn:aws:cloudfront::" + accountId + ":distribution/" + id);
        dist.setStatus("Deployed");
        dist.setDomainName(id.toLowerCase() + ".cloudfront.net");
        dist.setCreatedAt(now);
        dist.setLastModifiedTime(now);
        dist.setCallerReference(callerReference);
        dist.setComment(comment != null ? comment : "");
        dist.setEnabled(enabled);
        dist.setOrigins(origins != null ? origins : List.of());
        dist.setOriginDomainNames(origins != null
                ? origins.stream().map(Origin::getDomainName).toList()
                : List.of());
        dist.setDefaultRootObject(defaultRootObject != null ? defaultRootObject : "");
        dist.setDefaultCacheBehavior(defaultCacheBehavior);
        dist.setCacheBehaviors(cacheBehaviors != null ? cacheBehaviors : List.of());
        dist.setETag("E" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase());

        distributionStore.put(id, dist);
        return dist;
    }

    public Distribution getDistribution(String id) {
        return distributionStore.get(id)
                .orElseThrow(() -> new AwsException("NoSuchDistribution",
                        "The specified distribution does not exist.", 404));
    }

    public Distribution getDistributionByIdOrDomain(String idOrDomain) {
        Optional<Distribution> byId = distributionStore.get(idOrDomain);
        if (byId.isPresent()) return byId.get();
        return findByDomainName(idOrDomain)
                .orElseThrow(() -> new AwsException("NoSuchDistribution",
                        "The specified distribution does not exist.", 404));
    }

    public List<Distribution> listDistributions() {
        return distributionStore.scan(k -> true);
    }

    public Distribution updateDistribution(String id, String comment, boolean enabled,
                                           List<Origin> origins, String defaultRootObject,
                                           CacheBehavior defaultCacheBehavior,
                                           List<CacheBehavior> cacheBehaviors) {
        Distribution dist = getDistribution(id);
        if (comment != null) dist.setComment(comment);
        dist.setEnabled(enabled);
        if (origins != null && !origins.isEmpty()) {
            dist.setOrigins(origins);
            dist.setOriginDomainNames(origins.stream().map(Origin::getDomainName).toList());
        }
        if (defaultRootObject != null) dist.setDefaultRootObject(defaultRootObject);
        if (defaultCacheBehavior != null) dist.setDefaultCacheBehavior(defaultCacheBehavior);
        if (cacheBehaviors != null) dist.setCacheBehaviors(cacheBehaviors);
        dist.setLastModifiedTime(Instant.now().getEpochSecond());
        dist.setETag("E" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase());
        distributionStore.put(id, dist);
        return dist;
    }

    public void deleteDistribution(String id) {
        getDistribution(id);
        distributionStore.delete(id);
    }

    // ─────────────── Invalidations ───────────────

    public Invalidation createInvalidation(String distributionId, String callerReference, List<String> paths) {
        getDistribution(distributionId);

        String invId = UUID.randomUUID().toString().replace("-", "").substring(0, 14).toUpperCase();
        Invalidation inv = new Invalidation();
        inv.setId(invId);
        inv.setStatus("Completed");
        inv.setCreateTime(Instant.now().getEpochSecond());
        inv.setCallerReference(callerReference);
        inv.setDistributionId(distributionId);
        inv.setPaths(paths != null ? paths : List.of());

        invalidationStore.put(distributionId + "::" + invId, inv);
        return inv;
    }

    public List<Invalidation> listInvalidations(String distributionId) {
        getDistribution(distributionId);
        return invalidationStore.scan(k -> k.startsWith(distributionId + "::"));
    }

    public Invalidation getInvalidation(String distributionId, String invalidationId) {
        return invalidationStore.get(distributionId + "::" + invalidationId)
                .orElseThrow(() -> new AwsException("NoSuchInvalidation",
                        "The specified invalidation does not exist.", 404));
    }

    // ─────────────── Cache Policies ───────────────

    public CachePolicy createCachePolicy(String name, String comment,
                                         long defaultTTL, long maxTTL, long minTTL) {
        boolean duplicate = cachePolicyStore.scan(k -> true).stream()
                .anyMatch(p -> name.equals(p.getName()));
        if (duplicate) {
            throw new AwsException("CachePolicyAlreadyExists",
                    "A cache policy with this name already exists.", 409);
        }

        String id = UUID.randomUUID().toString();
        long now = Instant.now().getEpochSecond();

        CachePolicy policy = new CachePolicy();
        policy.setId(id);
        policy.setName(name);
        policy.setComment(comment != null ? comment : "");
        policy.setDefaultTTL(defaultTTL);
        policy.setMaxTTL(maxTTL);
        policy.setMinTTL(minTTL);
        policy.setLastModifiedTime(now);
        policy.setETag("E" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase());

        cachePolicyStore.put(id, policy);
        return policy;
    }

    public CachePolicy getCachePolicy(String id) {
        return cachePolicyStore.get(id)
                .orElseThrow(() -> new AwsException("NoSuchCachePolicy",
                        "The specified cache policy does not exist.", 404));
    }

    public List<CachePolicy> listCachePolicies() {
        return cachePolicyStore.scan(k -> true);
    }

    public CachePolicy updateCachePolicy(String id, String name, String comment,
                                         long defaultTTL, long maxTTL, long minTTL) {
        CachePolicy policy = getCachePolicy(id);
        if (name != null) policy.setName(name);
        if (comment != null) policy.setComment(comment);
        policy.setDefaultTTL(defaultTTL);
        policy.setMaxTTL(maxTTL);
        policy.setMinTTL(minTTL);
        policy.setLastModifiedTime(Instant.now().getEpochSecond());
        policy.setETag("E" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase());
        cachePolicyStore.put(id, policy);
        return policy;
    }

    public void deleteCachePolicy(String id) {
        getCachePolicy(id);
        cachePolicyStore.delete(id);
    }

    // ─────────────── Tags ───────────────

    public void tagResource(String arn, Map<String, String> tags) {
        Distribution dist = distributionStore.scan(k -> true).stream()
                .filter(d -> arn.equals(d.getArn()))
                .findFirst()
                .orElseThrow(() -> new AwsException("NoSuchDistribution",
                        "The specified distribution does not exist.", 404));
        dist.getTags().putAll(tags);
        distributionStore.put(dist.getId(), dist);
    }

    public void untagResource(String arn, List<String> tagKeys) {
        Distribution dist = distributionStore.scan(k -> true).stream()
                .filter(d -> arn.equals(d.getArn()))
                .findFirst()
                .orElseThrow(() -> new AwsException("NoSuchDistribution",
                        "The specified distribution does not exist.", 404));
        tagKeys.forEach(dist.getTags()::remove);
        distributionStore.put(dist.getId(), dist);
    }

    public Map<String, String> listTagsForResource(String arn) {
        return distributionStore.scan(k -> true).stream()
                .filter(d -> arn.equals(d.getArn()))
                .findFirst()
                .orElseThrow(() -> new AwsException("NoSuchDistribution",
                        "The specified distribution does not exist.", 404))
                .getTags();
    }

    // ─────────────── Proxy support ───────────────

    /**
     * Looks up a distribution by its assigned CloudFront domain name
     * (e.g. {@code abcd1234ef.cloudfront.net}).  The lookup is case-insensitive
     * and ignores a trailing port number in {@code domainName}.
     */
    public Optional<Distribution> findByDomainName(String domainName) {
        if (domainName == null) return Optional.empty();
        String host = domainName.contains(":") ? domainName.substring(0, domainName.indexOf(':')) : domainName;
        return distributionStore.scan(k -> true).stream()
                .filter(d -> host.equalsIgnoreCase(d.getDomainName()))
                .findFirst();
    }

    /**
     * Finds the Origin for the given request path using CloudFront's path-pattern
     * matching rules: the most-specific (longest) matching {@code CacheBehavior}
     * wins; if none matches, the {@code DefaultCacheBehavior} is used.
     */
    public Origin resolveOriginForPath(Distribution dist, String path) {
        CacheBehavior matched = null;
        for (CacheBehavior cb : dist.getCacheBehaviors()) {
            String pattern = cb.getPathPattern();
            if (pattern != null && pathMatches(path, pattern)) {
                if (matched == null || pattern.length() > matched.getPathPattern().length()) {
                    matched = cb;
                }
            }
        }

        String targetOriginId;
        if (matched != null) {
            targetOriginId = matched.getTargetOriginId();
        } else if (dist.getDefaultCacheBehavior() != null) {
            targetOriginId = dist.getDefaultCacheBehavior().getTargetOriginId();
        } else {
            return dist.getOrigins().isEmpty() ? null : dist.getOrigins().get(0);
        }

        if (targetOriginId == null) {
            return dist.getOrigins().isEmpty() ? null : dist.getOrigins().get(0);
        }

        final String id = targetOriginId;
        return dist.getOrigins().stream()
                .filter(o -> id.equals(o.getId()))
                .findFirst()
                .orElse(dist.getOrigins().isEmpty() ? null : dist.getOrigins().get(0));
    }

    private boolean pathMatches(String path, String pattern) {
        String regex = pattern
                .replace(".", "\\.")
                .replace("**", "§DSTAR§")
                .replace("*", "[^/]*")
                .replace("§DSTAR§", ".*");
        return path.matches(regex);
    }

    // ─────────────── Origin Access Controls ───────────────

    public OriginAccessControl createOriginAccessControl(String name, String description,
                                                          String signingBehavior, String signingProtocol,
                                                          String originType) {
        boolean duplicate = oacStore.scan(k -> true).stream()
                .anyMatch(o -> name.equals(o.getName()));
        if (duplicate) {
            throw new AwsException("OriginAccessControlAlreadyExists",
                    "An origin access control with this name already exists.", 409);
        }

        String id = UUID.randomUUID().toString();
        long now = Instant.now().getEpochSecond();

        OriginAccessControl oac = new OriginAccessControl();
        oac.setId(id);
        oac.setName(name);
        oac.setDescription(description != null ? description : "");
        oac.setSigningBehavior(signingBehavior != null ? signingBehavior : "always");
        oac.setSigningProtocol(signingProtocol != null ? signingProtocol : "sigv4");
        oac.setOriginAccessControlOriginType(originType != null ? originType : "s3");
        oac.setLastModifiedTime(now);
        oac.setETag("E" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase());

        oacStore.put(id, oac);
        return oac;
    }

    public OriginAccessControl getOriginAccessControl(String id) {
        return oacStore.get(id)
                .orElseThrow(() -> new AwsException("NoSuchOriginAccessControl",
                        "The specified origin access control does not exist.", 404));
    }

    public List<OriginAccessControl> listOriginAccessControls() {
        return oacStore.scan(k -> true);
    }

    public OriginAccessControl updateOriginAccessControl(String id, String name, String description,
                                                          String signingBehavior, String signingProtocol,
                                                          String originType) {
        OriginAccessControl oac = getOriginAccessControl(id);
        if (name != null) oac.setName(name);
        if (description != null) oac.setDescription(description);
        if (signingBehavior != null) oac.setSigningBehavior(signingBehavior);
        if (signingProtocol != null) oac.setSigningProtocol(signingProtocol);
        if (originType != null) oac.setOriginAccessControlOriginType(originType);
        oac.setLastModifiedTime(Instant.now().getEpochSecond());
        oac.setETag("E" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase());
        oacStore.put(id, oac);
        return oac;
    }

    public void deleteOriginAccessControl(String id) {
        getOriginAccessControl(id);
        oacStore.delete(id);
    }
}
