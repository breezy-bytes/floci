package io.github.hectorvent.floci.services.cloudfront;

import io.github.hectorvent.floci.core.common.XmlBuilder;
import io.github.hectorvent.floci.core.common.XmlParser;
import io.github.hectorvent.floci.services.cloudfront.model.CacheBehavior;
import io.github.hectorvent.floci.services.cloudfront.model.CachePolicy;
import io.github.hectorvent.floci.services.cloudfront.model.CustomHeader;
import io.github.hectorvent.floci.services.cloudfront.model.Distribution;
import io.github.hectorvent.floci.services.cloudfront.model.Invalidation;
import io.github.hectorvent.floci.services.cloudfront.model.Origin;
import io.github.hectorvent.floci.services.cloudfront.model.OriginAccessControl;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import org.jboss.logging.Logger;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import java.io.StringReader;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * CloudFront REST API controller.
 * Implements the CloudFront REST/XML API (2020-05-31).
 */
@Path("")
public class CloudFrontController {

    private static final Logger LOG = Logger.getLogger(CloudFrontController.class);
    private static final String NS = "http://cloudfront.amazonaws.com/doc/2020-05-31/";
    private static final DateTimeFormatter ISO_FORMAT = DateTimeFormatter.ISO_INSTANT;

    private final CloudFrontService service;

    @Inject
    public CloudFrontController(CloudFrontService service) {
        this.service = service;
    }

    // ─────────────── Distributions ───────────────

    @POST
    @Path("/2020-05-31/distribution")
    @Consumes({"text/xml", "application/xml", "*/*"})
    @Produces("text/xml")
    public Response createDistribution(@Context HttpHeaders headers, String body) {
        String callerRef = XmlParser.extractFirst(body, "CallerReference", null);
        String comment = XmlParser.extractFirst(body, "Comment", "");
        boolean enabled = "true".equalsIgnoreCase(XmlParser.extractFirst(body, "Enabled", "true"));
        String defaultRootObject = XmlParser.extractFirst(body, "DefaultRootObject", "");

        List<Origin> origins = parseOrigins(body);
        CacheBehavior defaultCacheBehavior = parseDefaultCacheBehavior(body);
        List<CacheBehavior> cacheBehaviors = parseCacheBehaviors(body);

        Distribution dist = service.createDistribution(callerRef, comment, enabled, origins,
                defaultRootObject, defaultCacheBehavior, cacheBehaviors);

        String xml = buildDistributionXml(dist);
        return Response.status(201)
                .entity(xml)
                .type("text/xml")
                .header("ETag", dist.getETag())
                .header("Location", "/2020-05-31/distribution/" + dist.getId())
                .build();
    }

    @GET
    @Path("/2020-05-31/distribution")
    @Produces("text/xml")
    public Response listDistributions(@Context HttpHeaders headers) {
        List<Distribution> distributions = service.listDistributions();
        String xml = buildDistributionListXml(distributions);
        return Response.ok(xml).type("text/xml").build();
    }

    @GET
    @Path("/2020-05-31/distribution/{Id}")
    @Produces("text/xml")
    public Response getDistribution(@Context HttpHeaders headers,
                                    @PathParam("Id") String id) {
        LOG.debug("hallo getDistribution");
        Distribution dist = service.getDistribution(id);
        String xml = buildDistributionXml(dist);
        return Response.ok(xml)
                .type("text/xml")
                .header("ETag", dist.getETag())
                .build();
    }

    @GET
    @Path("/2020-05-31/distribution/{Id}/config")
    @Produces("text/xml")
    public Response getDistributionConfig(@Context HttpHeaders headers,
                                          @PathParam("Id") String id) {
        Distribution dist = service.getDistribution(id);
        String xml = buildDistributionConfigXml(dist);
        return Response.ok(xml)
                .type("text/xml")
                .header("ETag", dist.getETag())
                .build();
    }

    @PUT
    @Path("/2020-05-31/distribution/{Id}/config")
    @Consumes({"text/xml", "application/xml", "*/*"})
    @Produces("text/xml")
    public Response updateDistribution(@Context HttpHeaders headers,
                                       @PathParam("Id") String id,
                                       String body) {
        String comment = XmlParser.extractFirst(body, "Comment", null);
        boolean enabled = "true".equalsIgnoreCase(XmlParser.extractFirst(body, "Enabled", "true"));
        String defaultRootObject = XmlParser.extractFirst(body, "DefaultRootObject", null);

        List<Origin> origins = parseOrigins(body);
        CacheBehavior defaultCacheBehavior = parseDefaultCacheBehavior(body);
        List<CacheBehavior> cacheBehaviors = parseCacheBehaviors(body);

        Distribution dist = service.updateDistribution(id, comment, enabled, origins,
                defaultRootObject, defaultCacheBehavior, cacheBehaviors);
        String xml = buildDistributionConfigXml(dist);
        return Response.ok(xml)
                .type("text/xml")
                .header("ETag", dist.getETag())
                .build();
    }

    @DELETE
    @Path("/2020-05-31/distribution/{Id}")
    public Response deleteDistribution(@Context HttpHeaders headers,
                                       @PathParam("Id") String id) {
        service.deleteDistribution(id);
        return Response.noContent().build();
    }

    // ─────────────── Invalidations ───────────────

    @POST
    @Path("/2020-05-31/distribution/{Id}/invalidation")
    @Consumes({"text/xml", "application/xml", "*/*"})
    @Produces("text/xml")
    public Response createInvalidation(@Context HttpHeaders headers,
                                       @PathParam("Id") String distributionId,
                                       String body) {
        String callerRef = XmlParser.extractFirst(body, "CallerReference", null);
        List<String> paths = XmlParser.extractAll(body, "Path");

        Invalidation inv = service.createInvalidation(distributionId, callerRef, paths);
        String xml = buildInvalidationXml(inv);
        return Response.status(201)
                .entity(xml)
                .type("text/xml")
                .header("Location", "/2020-05-31/distribution/" + distributionId + "/invalidation/" + inv.getId())
                .build();
    }

    @GET
    @Path("/2020-05-31/distribution/{Id}/invalidation")
    @Produces("text/xml")
    public Response listInvalidations(@Context HttpHeaders headers,
                                      @PathParam("Id") String distributionId) {
        List<Invalidation> invalidations = service.listInvalidations(distributionId);
        String xml = buildInvalidationListXml(distributionId, invalidations);
        return Response.ok(xml).type("text/xml").build();
    }

    @GET
    @Path("/2020-05-31/distribution/{Id}/invalidation/{InvId}")
    @Produces("text/xml")
    public Response getInvalidation(@Context HttpHeaders headers,
                                    @PathParam("Id") String distributionId,
                                    @PathParam("InvId") String invalidationId) {
        Invalidation inv = service.getInvalidation(distributionId, invalidationId);
        String xml = buildInvalidationXml(inv);
        return Response.ok(xml).type("text/xml").build();
    }

    // ─────────────── Cache Policies ───────────────

    @POST
    @Path("/2020-05-31/cache-policy")
    @Consumes({"text/xml", "application/xml", "*/*"})
    @Produces("text/xml")
    public Response createCachePolicy(@Context HttpHeaders headers, String body) {
        String name = XmlParser.extractFirst(body, "Name", null);
        String comment = XmlParser.extractFirst(body, "Comment", "");
        long defaultTTL = parseLong(XmlParser.extractFirst(body, "DefaultTTL", "86400"), 86400);
        long maxTTL = parseLong(XmlParser.extractFirst(body, "MaxTTL", "31536000"), 31536000);
        long minTTL = parseLong(XmlParser.extractFirst(body, "MinTTL", "0"), 0);

        CachePolicy policy = service.createCachePolicy(name, comment, defaultTTL, maxTTL, minTTL);
        String xml = buildCachePolicyXml(policy);
        return Response.status(201)
                .entity(xml)
                .type("text/xml")
                .header("ETag", policy.getETag())
                .header("Location", "/2020-05-31/cache-policy/" + policy.getId())
                .build();
    }

    @GET
    @Path("/2020-05-31/cache-policy")
    @Produces("text/xml")
    public Response listCachePolicies(@Context HttpHeaders headers) {
        List<CachePolicy> policies = service.listCachePolicies();
        String xml = buildCachePolicyListXml(policies);
        return Response.ok(xml).type("text/xml").build();
    }

    @GET
    @Path("/2020-05-31/cache-policy/{Id}")
    @Produces("text/xml")
    public Response getCachePolicy(@Context HttpHeaders headers,
                                   @PathParam("Id") String id) {
        CachePolicy policy = service.getCachePolicy(id);
        String xml = buildCachePolicyXml(policy);
        return Response.ok(xml)
                .type("text/xml")
                .header("ETag", policy.getETag())
                .build();
    }

    @PUT
    @Path("/2020-05-31/cache-policy/{Id}")
    @Consumes({"text/xml", "application/xml", "*/*"})
    @Produces("text/xml")
    public Response updateCachePolicy(@Context HttpHeaders headers,
                                      @PathParam("Id") String id,
                                      String body) {
        String name = XmlParser.extractFirst(body, "Name", null);
        String comment = XmlParser.extractFirst(body, "Comment", null);
        long defaultTTL = parseLong(XmlParser.extractFirst(body, "DefaultTTL", "86400"), 86400);
        long maxTTL = parseLong(XmlParser.extractFirst(body, "MaxTTL", "31536000"), 31536000);
        long minTTL = parseLong(XmlParser.extractFirst(body, "MinTTL", "0"), 0);

        CachePolicy policy = service.updateCachePolicy(id, name, comment, defaultTTL, maxTTL, minTTL);
        String xml = buildCachePolicyXml(policy);
        return Response.ok(xml)
                .type("text/xml")
                .header("ETag", policy.getETag())
                .build();
    }

    @DELETE
    @Path("/2020-05-31/cache-policy/{Id}")
    public Response deleteCachePolicy(@Context HttpHeaders headers,
                                      @PathParam("Id") String id) {
        service.deleteCachePolicy(id);
        return Response.noContent().build();
    }

    // ─────────────── Tags ───────────────

    @GET
    @Path("/2020-05-31/tags")
    @Produces("text/xml")
    public Response listTagsForResource(@Context HttpHeaders headers,
                                        @Context UriInfo uriInfo) {
        String arn = uriInfo.getQueryParameters().getFirst("Resource");
        Map<String, String> tags = service.listTagsForResource(arn);
        String xml = buildTagsXml(tags);
        return Response.ok(xml).type("text/xml").build();
    }

    @POST
    @Path("/2020-05-31/tagging")
    @Consumes({"text/xml", "application/xml", "*/*"})
    @Produces("text/xml")
    public Response tagging(@Context HttpHeaders headers,
                            @Context UriInfo uriInfo,
                            String body) {
        String arn = uriInfo.getQueryParameters().getFirst("Resource");
        String operation = uriInfo.getQueryParameters().getFirst("Operation");

        if ("Tag".equals(operation)) {
            Map<String, String> tags = XmlParser.extractPairs(body, "Tag", "Key", "Value");
            service.tagResource(arn, tags);
            return Response.noContent().build();
        } else if ("Untag".equals(operation)) {
            List<String> keys = XmlParser.extractAll(body, "Key");
            service.untagResource(arn, keys);
            return Response.noContent().build();
        }
        return Response.status(400).build();
    }

    // ─────────────── XML parsing helpers ───────────────

    /**
     * Full StAX-based Origin parser that handles nested structures:
     * CustomHeaders > Items > OriginCustomHeader, S3OriginConfig, CustomOriginConfig.
     * Uses a path stack to determine context rather than a depth counter, so that
     * calling getElementText() for leaf elements doesn't corrupt depth tracking.
     */
    private List<Origin> parseOrigins(String body) {
        List<Origin> origins = new ArrayList<>();
        if (body == null || body.isEmpty()) return origins;
        try {
            XMLInputFactory factory = XMLInputFactory.newInstance();
            factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
            factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
            XMLStreamReader r = factory.createXMLStreamReader(new StringReader(body));

            Origin current = null;
            CustomHeader currentHeader = null;
            Deque<String> stack = new ArrayDeque<>();
            StringBuilder text = new StringBuilder();

            while (r.hasNext()) {
                int event = r.next();
                switch (event) {
                    case XMLStreamConstants.START_ELEMENT -> {
                        stack.push(r.getLocalName());
                        text.setLength(0);
                        String name = r.getLocalName();
                        String parent = parentOf(stack);

                        if ("Origin".equals(name) && ("Items".equals(parent) || "Origins".equals(parent))) {
                            current = new Origin();
                        } else if ("OriginCustomHeader".equals(name)) {
                            currentHeader = new CustomHeader();
                        }
                    }
                    case XMLStreamConstants.CHARACTERS, XMLStreamConstants.CDATA ->
                            text.append(r.getText());
                    case XMLStreamConstants.END_ELEMENT -> {
                        String name = r.getLocalName();
                        stack.pop();
                        String trimmed = text.toString().trim();
                        // After pop the top of the stack IS the direct parent element.
                        String parent = stack.isEmpty() ? "" : stack.peek();
                        text.setLength(0);

                        if (current == null) break;

                        if ("Origin".equals(name)) {
                            origins.add(current);
                            current = null;
                        } else if ("OriginCustomHeader".equals(name) && currentHeader != null) {
                            current.getCustomHeaders().add(currentHeader);
                            currentHeader = null;
                        } else if (currentHeader != null) {
                            // Inside OriginCustomHeader leaf
                            if ("HeaderName".equals(name)) currentHeader.setHeaderName(trimmed);
                            else if ("HeaderValue".equals(name)) currentHeader.setHeaderValue(trimmed);
                        } else if ("Origin".equals(parent)) {
                            // Direct child of Origin
                            switch (name) {
                                case "Id"                    -> current.setId(trimmed);
                                case "DomainName"            -> current.setDomainName(trimmed);
                                case "OriginPath"            -> current.setOriginPath(trimmed);
                                case "OriginAccessControlId" -> current.setOriginAccessControlId(trimmed);
                            }
                        } else if ("S3OriginConfig".equals(parent)) {
                            if ("OriginAccessIdentity".equals(name)) current.setOriginAccessIdentity(trimmed);
                        } else if ("CustomOriginConfig".equals(parent)) {
                            if ("OriginProtocolPolicy".equals(name)) current.setProtocol(trimmed);
                        }
                    }
                }
            }
            r.close();
        } catch (Exception ignored) {}
        return origins;
    }

    /** Returns the element one level above the top of the stack (i.e. the parent element name). */
    private static String parentOf(Deque<String> stack) {
        if (stack.size() < 2) return "";
        String top = stack.peek();
        stack.pop();
        String parent = stack.isEmpty() ? "" : stack.peek();
        stack.push(top);
        return parent;
    }

    /**
     * Parses DefaultCacheBehavior using extractFirst, which naturally picks up
     * the first occurrence of each tag — those belong to DefaultCacheBehavior
     * since it appears before CacheBehaviors in the standard CloudFront XML ordering.
     */
    private CacheBehavior parseDefaultCacheBehavior(String body) {
        String targetOriginId = XmlParser.extractFirst(body, "TargetOriginId", null);
        if (targetOriginId == null) return null;

        CacheBehavior cb = new CacheBehavior();
        cb.setTargetOriginId(targetOriginId);
        cb.setViewerProtocolPolicy(XmlParser.extractFirst(body, "ViewerProtocolPolicy", "allow-all"));
        cb.setCachePolicyId(XmlParser.extractFirst(body, "CachePolicyId", null));
        cb.setDefaultTTL(parseLong(XmlParser.extractFirst(body, "DefaultTTL", "86400"), 86400));
        cb.setMaxTTL(parseLong(XmlParser.extractFirst(body, "MaxTTL", "31536000"), 31536000));
        cb.setMinTTL(parseLong(XmlParser.extractFirst(body, "MinTTL", "0"), 0));
        return cb;
    }

    private List<CacheBehavior> parseCacheBehaviors(String body) {
        return XmlParser.extractGroups(body, "CacheBehavior").stream()
                .map(g -> {
                    CacheBehavior cb = new CacheBehavior();
                    cb.setPathPattern(g.getOrDefault("PathPattern", ""));
                    cb.setTargetOriginId(g.getOrDefault("TargetOriginId", ""));
                    cb.setViewerProtocolPolicy(g.getOrDefault("ViewerProtocolPolicy", "allow-all"));
                    cb.setCachePolicyId(g.getOrDefault("CachePolicyId", null));
                    cb.setDefaultTTL(parseLong(g.get("DefaultTTL"), 86400));
                    cb.setMaxTTL(parseLong(g.get("MaxTTL"), 31536000));
                    cb.setMinTTL(parseLong(g.get("MinTTL"), 0));
                    return cb;
                })
                .collect(Collectors.toList());
    }

    private long parseLong(String value, long defaultValue) {
        if (value == null) return defaultValue;
        try { return Long.parseLong(value.trim()); }
        catch (NumberFormatException e) { return defaultValue; }
    }

    // ─────────────── XML builders ───────────────

    private String buildDistributionXml(Distribution dist) {
        return new XmlBuilder()
                .start("Distribution", NS)
                  .elem("Id", dist.getId())
                  .elem("ARN", dist.getArn())
                  .elem("Status", dist.getStatus())
                  .elem("DomainName", dist.getDomainName())
                  .elem("LastModifiedTime", formatTime(dist.getLastModifiedTime()))
                  .raw(buildDistributionConfigFragment(dist))
                .end("Distribution")
                .build();
    }

    private String buildDistributionConfigXml(Distribution dist) {
        return new XmlBuilder()
                .start("DistributionConfig", NS)
                  .raw(distributionConfigFields(dist))
                .end("DistributionConfig")
                .build();
    }

    private String buildDistributionConfigFragment(Distribution dist) {
        return new XmlBuilder()
                .start("DistributionConfig")
                  .raw(distributionConfigFields(dist))
                .end("DistributionConfig")
                .build();
    }

    private String distributionConfigFields(Distribution dist) {
        XmlBuilder b = new XmlBuilder()
                .elem("CallerReference", dist.getCallerReference())
                .elem("Comment", dist.getComment())
                .elem("Enabled", dist.isEnabled())
                .elem("DefaultRootObject", dist.getDefaultRootObject());

        // Origins — prefer structured origins, fall back to originDomainNames
        List<Origin> origins = dist.getOrigins();
        if (!origins.isEmpty()) {
            b.start("Origins").elem("Quantity", origins.size()).start("Items");
            for (Origin o : origins) {
                b.start("Origin")
                 .elem("Id", o.getId())
                 .elem("DomainName", o.getDomainName());
                if (o.getOriginPath() != null && !o.getOriginPath().isEmpty()) {
                    b.elem("OriginPath", o.getOriginPath());
                }
                if (o.getOriginAccessControlId() != null && !o.getOriginAccessControlId().isEmpty()) {
                    b.elem("OriginAccessControlId", o.getOriginAccessControlId());
                }
                if (o.getOriginAccessIdentity() != null && !o.getOriginAccessIdentity().isEmpty()) {
                    b.start("S3OriginConfig")
                     .elem("OriginAccessIdentity", o.getOriginAccessIdentity())
                     .end("S3OriginConfig");
                }
                if (o.getProtocol() != null && !o.getProtocol().isEmpty()) {
                    b.start("CustomOriginConfig")
                     .elem("OriginProtocolPolicy", o.getProtocol())
                     .end("CustomOriginConfig");
                }
                List<CustomHeader> headers = o.getCustomHeaders();
                if (!headers.isEmpty()) {
                    b.start("CustomHeaders")
                     .elem("Quantity", headers.size())
                     .start("Items");
                    for (CustomHeader h : headers) {
                        b.start("OriginCustomHeader")
                         .elem("HeaderName", h.getHeaderName())
                         .elem("HeaderValue", h.getHeaderValue())
                         .end("OriginCustomHeader");
                    }
                    b.end("Items").end("CustomHeaders");
                }
                b.end("Origin");
            }
            b.end("Items").end("Origins");
        } else {
            List<String> domainNames = dist.getOriginDomainNames();
            if (!domainNames.isEmpty()) {
                b.start("Origins").elem("Quantity", domainNames.size()).start("Items");
                for (int i = 0; i < domainNames.size(); i++) {
                    b.start("Origin")
                     .elem("Id", "origin-" + (i + 1))
                     .elem("DomainName", domainNames.get(i))
                     .end("Origin");
                }
                b.end("Items").end("Origins");
            }
        }

        // DefaultCacheBehavior
        CacheBehavior dcb = dist.getDefaultCacheBehavior();
        if (dcb != null) {
            b.start("DefaultCacheBehavior")
             .elem("TargetOriginId", dcb.getTargetOriginId())
             .elem("ViewerProtocolPolicy", dcb.getViewerProtocolPolicy())
             .elem("CachePolicyId", dcb.getCachePolicyId())
             .elem("DefaultTTL", dcb.getDefaultTTL())
             .elem("MaxTTL", dcb.getMaxTTL())
             .elem("MinTTL", dcb.getMinTTL())
             .end("DefaultCacheBehavior");
        }

        // CacheBehaviors
        List<CacheBehavior> behaviors = dist.getCacheBehaviors();
        b.start("CacheBehaviors").elem("Quantity", behaviors.size());
        if (!behaviors.isEmpty()) {
            b.start("Items");
            for (CacheBehavior cb : behaviors) {
                b.start("CacheBehavior")
                 .elem("PathPattern", cb.getPathPattern())
                 .elem("TargetOriginId", cb.getTargetOriginId())
                 .elem("ViewerProtocolPolicy", cb.getViewerProtocolPolicy())
                 .elem("CachePolicyId", cb.getCachePolicyId())
                 .elem("DefaultTTL", cb.getDefaultTTL())
                 .elem("MaxTTL", cb.getMaxTTL())
                 .elem("MinTTL", cb.getMinTTL())
                 .end("CacheBehavior");
            }
            b.end("Items");
        }
        b.end("CacheBehaviors");

        return b.build();
    }

    private String buildDistributionListXml(List<Distribution> distributions) {
        XmlBuilder b = new XmlBuilder()
                .start("DistributionList", NS)
                  .elem("Marker", "")
                  .elem("MaxItems", 100)
                  .elem("IsTruncated", false)
                  .elem("Quantity", distributions.size());

        if (!distributions.isEmpty()) {
            b.start("Items");
            for (Distribution dist : distributions) {
                b.start("DistributionSummary")
                 .elem("Id", dist.getId())
                 .elem("ARN", dist.getArn())
                 .elem("Status", dist.getStatus())
                 .elem("DomainName", dist.getDomainName())
                 .elem("Comment", dist.getComment())
                 .elem("Enabled", dist.isEnabled())
                 .elem("LastModifiedTime", formatTime(dist.getLastModifiedTime()))
                 .end("DistributionSummary");
            }
            b.end("Items");
        }

        b.end("DistributionList");
        return b.build();
    }

    private String buildInvalidationXml(Invalidation inv) {
        XmlBuilder b = new XmlBuilder()
                .start("Invalidation", NS)
                  .elem("Id", inv.getId())
                  .elem("Status", inv.getStatus())
                  .elem("CreateTime", formatTime(inv.getCreateTime()))
                  .start("InvalidationBatch")
                    .elem("CallerReference", inv.getCallerReference())
                    .start("Paths")
                      .elem("Quantity", inv.getPaths().size());

        if (!inv.getPaths().isEmpty()) {
            b.start("Items");
            for (String path : inv.getPaths()) {
                b.elem("Path", path);
            }
            b.end("Items");
        }

        b.end("Paths")
         .end("InvalidationBatch")
         .end("Invalidation");
        return b.build();
    }

    private String buildInvalidationListXml(String distributionId, List<Invalidation> invalidations) {
        XmlBuilder b = new XmlBuilder()
                .start("InvalidationList", NS)
                  .elem("Marker", "")
                  .elem("MaxItems", 100)
                  .elem("IsTruncated", false)
                  .elem("Quantity", invalidations.size());

        if (!invalidations.isEmpty()) {
            b.start("Items");
            for (Invalidation inv : invalidations) {
                b.start("InvalidationSummary")
                 .elem("Id", inv.getId())
                 .elem("Status", inv.getStatus())
                 .elem("CreateTime", formatTime(inv.getCreateTime()))
                 .end("InvalidationSummary");
            }
            b.end("Items");
        }

        b.end("InvalidationList");
        return b.build();
    }

    private String buildCachePolicyXml(CachePolicy policy) {
        return new XmlBuilder()
                .start("CachePolicy", NS)
                  .elem("Id", policy.getId())
                  .elem("LastModifiedTime", formatTime(policy.getLastModifiedTime()))
                  .start("CachePolicyConfig")
                    .elem("Name", policy.getName())
                    .elem("Comment", policy.getComment())
                    .elem("DefaultTTL", policy.getDefaultTTL())
                    .elem("MaxTTL", policy.getMaxTTL())
                    .elem("MinTTL", policy.getMinTTL())
                  .end("CachePolicyConfig")
                .end("CachePolicy")
                .build();
    }

    private String buildCachePolicyListXml(List<CachePolicy> policies) {
        XmlBuilder b = new XmlBuilder()
                .start("CachePolicyList", NS)
                  .elem("MaxItems", 100)
                  .elem("Quantity", policies.size());

        if (!policies.isEmpty()) {
            b.start("Items");
            for (CachePolicy policy : policies) {
                b.start("CachePolicySummary")
                 .elem("Type", "custom")
                 .start("CachePolicy")
                   .elem("Id", policy.getId())
                   .elem("LastModifiedTime", formatTime(policy.getLastModifiedTime()))
                   .start("CachePolicyConfig")
                     .elem("Name", policy.getName())
                     .elem("Comment", policy.getComment())
                     .elem("DefaultTTL", policy.getDefaultTTL())
                     .elem("MaxTTL", policy.getMaxTTL())
                     .elem("MinTTL", policy.getMinTTL())
                   .end("CachePolicyConfig")
                 .end("CachePolicy")
                 .end("CachePolicySummary");
            }
            b.end("Items");
        }

        b.end("CachePolicyList");
        return b.build();
    }

    private String buildTagsXml(Map<String, String> tags) {
        XmlBuilder b = new XmlBuilder()
                .start("Tags", NS)
                  .start("Items");
        tags.forEach((k, v) -> b.start("Tag")
                .elem("Key", k)
                .elem("Value", v)
                .end("Tag"));
        b.end("Items").end("Tags");
        return b.build();
    }

    // ─────────────── Origin Access Controls ───────────────

    @POST
    @Path("/2020-05-31/origin-access-control")
    @Consumes({"text/xml", "application/xml", "*/*"})
    @Produces("text/xml")
    public Response createOriginAccessControl(@Context HttpHeaders headers, String body) {
        String name            = XmlParser.extractFirst(body, "Name", null);
        String description     = XmlParser.extractFirst(body, "Description", "");
        String signingBehavior = XmlParser.extractFirst(body, "SigningBehavior", "always");
        String signingProtocol = XmlParser.extractFirst(body, "SigningProtocol", "sigv4");
        String originType      = XmlParser.extractFirst(body, "OriginAccessControlOriginType", "s3");

        OriginAccessControl oac = service.createOriginAccessControl(
                name, description, signingBehavior, signingProtocol, originType);
        String xml = buildOacXml(oac);
        return Response.status(201)
                .entity(xml)
                .type("text/xml")
                .header("ETag", oac.getETag())
                .header("Location", "/2020-05-31/origin-access-control/" + oac.getId())
                .build();
    }

    @GET
    @Path("/2020-05-31/origin-access-control")
    @Produces("text/xml")
    public Response listOriginAccessControls(@Context HttpHeaders headers) {
        List<OriginAccessControl> oacs = service.listOriginAccessControls();
        String xml = buildOacListXml(oacs);
        return Response.ok(xml).type("text/xml").build();
    }

    @GET
    @Path("/2020-05-31/origin-access-control/{Id}")
    @Produces("text/xml")
    public Response getOriginAccessControl(@Context HttpHeaders headers,
                                           @PathParam("Id") String id) {
        OriginAccessControl oac = service.getOriginAccessControl(id);
        String xml = buildOacXml(oac);
        return Response.ok(xml)
                .type("text/xml")
                .header("ETag", oac.getETag())
                .build();
    }

    @GET
    @Path("/2020-05-31/origin-access-control/{Id}/config")
    @Produces("text/xml")
    public Response getOriginAccessControlConfig(@Context HttpHeaders headers,
                                                  @PathParam("Id") String id) {
        OriginAccessControl oac = service.getOriginAccessControl(id);
        String xml = buildOacConfigXml(oac);
        return Response.ok(xml)
                .type("text/xml")
                .header("ETag", oac.getETag())
                .build();
    }

    @PUT
    @Path("/2020-05-31/origin-access-control/{Id}/config")
    @Consumes({"text/xml", "application/xml", "*/*"})
    @Produces("text/xml")
    public Response updateOriginAccessControl(@Context HttpHeaders headers,
                                              @PathParam("Id") String id,
                                              String body) {
        String name            = XmlParser.extractFirst(body, "Name", null);
        String description     = XmlParser.extractFirst(body, "Description", null);
        String signingBehavior = XmlParser.extractFirst(body, "SigningBehavior", null);
        String signingProtocol = XmlParser.extractFirst(body, "SigningProtocol", null);
        String originType      = XmlParser.extractFirst(body, "OriginAccessControlOriginType", null);

        OriginAccessControl oac = service.updateOriginAccessControl(
                id, name, description, signingBehavior, signingProtocol, originType);
        String xml = buildOacConfigXml(oac);
        return Response.ok(xml)
                .type("text/xml")
                .header("ETag", oac.getETag())
                .build();
    }

    @DELETE
    @Path("/2020-05-31/origin-access-control/{Id}")
    public Response deleteOriginAccessControl(@Context HttpHeaders headers,
                                              @PathParam("Id") String id) {
        service.deleteOriginAccessControl(id);
        return Response.noContent().build();
    }

    // ─────────────── OAC XML builders ───────────────

    private String buildOacXml(OriginAccessControl oac) {
        return new XmlBuilder()
                .start("OriginAccessControl", NS)
                  .elem("Id", oac.getId())
                  .elem("LastModifiedTime", formatTime(oac.getLastModifiedTime()))
                  .raw(oacConfigFields(oac))
                .end("OriginAccessControl")
                .build();
    }

    private String buildOacConfigXml(OriginAccessControl oac) {
        return new XmlBuilder()
                .start("OriginAccessControlConfig", NS)
                  .raw(oacConfigFields(oac))
                .end("OriginAccessControlConfig")
                .build();
    }

    private String oacConfigFields(OriginAccessControl oac) {
        return new XmlBuilder()
                .elem("Name", oac.getName())
                .elem("Description", oac.getDescription())
                .elem("SigningBehavior", oac.getSigningBehavior())
                .elem("SigningProtocol", oac.getSigningProtocol())
                .elem("OriginAccessControlOriginType", oac.getOriginAccessControlOriginType())
                .build();
    }

    private String buildOacListXml(List<OriginAccessControl> oacs) {
        XmlBuilder b = new XmlBuilder()
                .start("OriginAccessControlList", NS)
                  .elem("MaxItems", 100)
                  .elem("IsTruncated", false)
                  .elem("Quantity", oacs.size());

        if (!oacs.isEmpty()) {
            b.start("Items");
            for (OriginAccessControl oac : oacs) {
                b.start("OriginAccessControlSummary")
                 .elem("Id", oac.getId())
                 .elem("Name", oac.getName())
                 .elem("Description", oac.getDescription())
                 .elem("SigningBehavior", oac.getSigningBehavior())
                 .elem("SigningProtocol", oac.getSigningProtocol())
                 .elem("OriginAccessControlOriginType", oac.getOriginAccessControlOriginType())
                 .elem("LastModifiedTime", formatTime(oac.getLastModifiedTime()))
                 .end("OriginAccessControlSummary");
            }
            b.end("Items");
        }

        b.end("OriginAccessControlList");
        return b.build();
    }

    private String formatTime(long epochSeconds) {
        return ISO_FORMAT.format(Instant.ofEpochSecond(epochSeconds));
    }
}
