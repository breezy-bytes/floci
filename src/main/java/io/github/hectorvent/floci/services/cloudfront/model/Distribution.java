package io.github.hectorvent.floci.services.cloudfront.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.quarkus.runtime.annotations.RegisterForReflection;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RegisterForReflection
@JsonIgnoreProperties(ignoreUnknown = true)
public class Distribution {

    private String id;
    private String arn;
    private String status;
    private String domainName;
    private long createdAt;
    private long lastModifiedTime;
    private String callerReference;
    private String comment;
    private boolean enabled;
    private String eTag;
    private List<String> originDomainNames = new ArrayList<>();
    private List<Origin> origins = new ArrayList<>();
    private String defaultRootObject;
    private CacheBehavior defaultCacheBehavior;
    private List<CacheBehavior> cacheBehaviors = new ArrayList<>();
    private Map<String, String> tags = new HashMap<>();

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getArn() { return arn; }
    public void setArn(String arn) { this.arn = arn; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getDomainName() { return domainName; }
    public void setDomainName(String domainName) { this.domainName = domainName; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public long getLastModifiedTime() { return lastModifiedTime; }
    public void setLastModifiedTime(long lastModifiedTime) { this.lastModifiedTime = lastModifiedTime; }

    public String getCallerReference() { return callerReference; }
    public void setCallerReference(String callerReference) { this.callerReference = callerReference; }

    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getETag() { return eTag; }
    public void setETag(String eTag) { this.eTag = eTag; }

    public List<String> getOriginDomainNames() { return originDomainNames; }
    public void setOriginDomainNames(List<String> originDomainNames) { this.originDomainNames = originDomainNames; }

    public String getDefaultRootObject() { return defaultRootObject; }
    public void setDefaultRootObject(String defaultRootObject) { this.defaultRootObject = defaultRootObject; }

    public List<Origin> getOrigins() { return origins; }
    public void setOrigins(List<Origin> origins) { this.origins = origins; }

    public CacheBehavior getDefaultCacheBehavior() { return defaultCacheBehavior; }
    public void setDefaultCacheBehavior(CacheBehavior defaultCacheBehavior) { this.defaultCacheBehavior = defaultCacheBehavior; }

    public List<CacheBehavior> getCacheBehaviors() { return cacheBehaviors; }
    public void setCacheBehaviors(List<CacheBehavior> cacheBehaviors) { this.cacheBehaviors = cacheBehaviors; }

    public Map<String, String> getTags() { return tags; }
    public void setTags(Map<String, String> tags) { this.tags = tags; }
}
