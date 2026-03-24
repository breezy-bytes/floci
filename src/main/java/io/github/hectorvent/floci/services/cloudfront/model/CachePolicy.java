package io.github.hectorvent.floci.services.cloudfront.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
@JsonIgnoreProperties(ignoreUnknown = true)
public class CachePolicy {

    private String id;
    private String name;
    private String comment;
    private long defaultTTL = 86400;
    private long maxTTL = 31536000;
    private long minTTL = 0;
    private long lastModifiedTime;
    private String eTag;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }

    public long getDefaultTTL() { return defaultTTL; }
    public void setDefaultTTL(long defaultTTL) { this.defaultTTL = defaultTTL; }

    public long getMaxTTL() { return maxTTL; }
    public void setMaxTTL(long maxTTL) { this.maxTTL = maxTTL; }

    public long getMinTTL() { return minTTL; }
    public void setMinTTL(long minTTL) { this.minTTL = minTTL; }

    public long getLastModifiedTime() { return lastModifiedTime; }
    public void setLastModifiedTime(long lastModifiedTime) { this.lastModifiedTime = lastModifiedTime; }

    public String getETag() { return eTag; }
    public void setETag(String eTag) { this.eTag = eTag; }
}
