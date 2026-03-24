package io.github.hectorvent.floci.services.cloudfront.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.quarkus.runtime.annotations.RegisterForReflection;

import java.util.ArrayList;
import java.util.List;

@RegisterForReflection
@JsonIgnoreProperties(ignoreUnknown = true)
public class Origin {

    private String id;
    private String domainName;
    private String originPath;
    private String originAccessIdentity;
    private String protocol; // http-only, https-only, match-viewer

    /** OAC reference (successor to originAccessIdentity). */
    private String originAccessControlId;

    /** Custom headers injected into every request forwarded to this origin. */
    private List<CustomHeader> customHeaders = new ArrayList<>();

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getDomainName() { return domainName; }
    public void setDomainName(String domainName) { this.domainName = domainName; }

    public String getOriginPath() { return originPath; }
    public void setOriginPath(String originPath) { this.originPath = originPath; }

    public String getOriginAccessIdentity() { return originAccessIdentity; }
    public void setOriginAccessIdentity(String originAccessIdentity) { this.originAccessIdentity = originAccessIdentity; }

    public String getProtocol() { return protocol; }
    public void setProtocol(String protocol) { this.protocol = protocol; }

    public String getOriginAccessControlId() { return originAccessControlId; }
    public void setOriginAccessControlId(String originAccessControlId) {
        this.originAccessControlId = originAccessControlId;
    }

    public List<CustomHeader> getCustomHeaders() { return customHeaders; }
    public void setCustomHeaders(List<CustomHeader> customHeaders) {
        this.customHeaders = customHeaders != null ? customHeaders : new ArrayList<>();
    }
}