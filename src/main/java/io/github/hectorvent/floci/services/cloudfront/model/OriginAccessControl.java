package io.github.hectorvent.floci.services.cloudfront.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * Represents an Origin Access Control (OAC) configuration.
 * OAC is the successor to Origin Access Identity (OAI) and is used to
 * restrict S3 (and other origin) access to CloudFront only.
 */
@RegisterForReflection
@JsonIgnoreProperties(ignoreUnknown = true)
public class OriginAccessControl {

    private String id;
    private String name;
    private String description;

    /** Signing behavior: always | never | no-override */
    private String signingBehavior;

    /** Signing protocol: sigv4 */
    private String signingProtocol;

    /** Origin type: s3 | mediastore | mediapackagev2 | lambda */
    private String originAccessControlOriginType;

    private String eTag;
    private long lastModifiedTime;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getSigningBehavior() { return signingBehavior; }
    public void setSigningBehavior(String signingBehavior) { this.signingBehavior = signingBehavior; }

    public String getSigningProtocol() { return signingProtocol; }
    public void setSigningProtocol(String signingProtocol) { this.signingProtocol = signingProtocol; }

    public String getOriginAccessControlOriginType() { return originAccessControlOriginType; }
    public void setOriginAccessControlOriginType(String originAccessControlOriginType) {
        this.originAccessControlOriginType = originAccessControlOriginType;
    }

    public String getETag() { return eTag; }
    public void setETag(String eTag) { this.eTag = eTag; }

    public long getLastModifiedTime() { return lastModifiedTime; }
    public void setLastModifiedTime(long lastModifiedTime) { this.lastModifiedTime = lastModifiedTime; }
}
