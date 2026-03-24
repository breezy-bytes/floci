package io.github.hectorvent.floci.services.cloudfront.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
@JsonIgnoreProperties(ignoreUnknown = true)
public class CacheBehavior {

    /** Null for the DefaultCacheBehavior. */
    private String pathPattern;
    private String targetOriginId;
    private String viewerProtocolPolicy; // allow-all, https-only, redirect-to-https
    private String cachePolicyId;
    private long defaultTTL = 86400;
    private long maxTTL = 31536000;
    private long minTTL = 0;

    public String getPathPattern() { return pathPattern; }
    public void setPathPattern(String pathPattern) { this.pathPattern = pathPattern; }

    public String getTargetOriginId() { return targetOriginId; }
    public void setTargetOriginId(String targetOriginId) { this.targetOriginId = targetOriginId; }

    public String getViewerProtocolPolicy() { return viewerProtocolPolicy; }
    public void setViewerProtocolPolicy(String viewerProtocolPolicy) { this.viewerProtocolPolicy = viewerProtocolPolicy; }

    public String getCachePolicyId() { return cachePolicyId; }
    public void setCachePolicyId(String cachePolicyId) { this.cachePolicyId = cachePolicyId; }

    public long getDefaultTTL() { return defaultTTL; }
    public void setDefaultTTL(long defaultTTL) { this.defaultTTL = defaultTTL; }

    public long getMaxTTL() { return maxTTL; }
    public void setMaxTTL(long maxTTL) { this.maxTTL = maxTTL; }

    public long getMinTTL() { return minTTL; }
    public void setMinTTL(long minTTL) { this.minTTL = minTTL; }
}
