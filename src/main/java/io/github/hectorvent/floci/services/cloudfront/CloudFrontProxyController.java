package io.github.hectorvent.floci.services.cloudfront;

import io.github.hectorvent.floci.services.cloudfront.model.CustomHeader;
import io.github.hectorvent.floci.services.cloudfront.model.Distribution;
import io.github.hectorvent.floci.services.cloudfront.model.Origin;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.*;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

/**
 * HTTP-proxy controller for CloudFront distributions.
 *
 * <p>Route: {@code GET /distributions/{id}[/{path}]}</p>
 *
 * <p>Loads the matching CloudFront Behavior/Origin for the requested path and
 * forwards the request via HTTP to the appropriate backend:
 * <ul>
 *   <li><b>S3 origins</b> ({@code *.s3.*}) – forwarded to the local S3 emulator
 *       at {@code http://localhost:{port}/{bucket}/{path}}.</li>
 *   <li><b>API Gateway origins</b> ({@code *.execute-api.*}) – forwarded to the
 *       local API Gateway emulator at
 *       {@code http://localhost:{port}/execute-api/{apiId}/{path}}.</li>
 *   <li><b>Custom / arbitrary origins</b> – forwarded via HTTP to the external
 *       hostname.</li>
 * </ul>
 *
 * <p>Using HTTP for all origin types (rather than internal JAX-RS URI rewrites)
 * keeps the request pipeline extensible: future Lambda@Edge and CloudFront
 * Functions interceptors can manipulate the {@link HttpRequest} / response
 * objects before and after the upstream call.</p>
 */
@Path("/distributions")
public class CloudFrontProxyController {

    private static final Logger LOG = Logger.getLogger(CloudFrontProxyController.class);

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    /**
     * Hop-by-hop headers (RFC 7230) that apply only to a single connection and must not be
     * forwarded to the upstream origin. CloudFront strips these headers identically and replaces
     * {@code Host} with the configured origin domain name.
     */
    private static final List<String> HOP_BY_HOP = List.of(
            "host", "content-length", "transfer-encoding", "connection",
            "keep-alive", "proxy-authenticate", "proxy-authorization",
            "te", "trailer", "upgrade"
    );

    private final CloudFrontService cfService;
    private final String baseUrl;

    @Inject
    public CloudFrontProxyController(
            CloudFrontService cfService,
            @ConfigProperty(name = "quarkus.http.port") int serverPort
    ) {
        this.cfService = cfService;
        this.baseUrl = "http://localhost:" + serverPort;
    }

    // ─────────────── Routes ───────────────

    /**
     * Proxy request to the root path ({@code /}) of the distribution's resolved origin.
     */
    @GET
    @Path("/{id}")
    public Response proxyRoot(@PathParam("id") String id,
                              @Context HttpHeaders headers,
                              @Context UriInfo uriInfo) throws IOException {
        return proxy(id, "", headers.getRequestHeaders(), uriInfo.getRequestUri().getRawQuery());
    }

    /**
     * Proxy request to the given sub-path of the distribution's resolved origin.
     *
     * @param path the path relative to the distribution root (without leading slash)
     */
    @GET
    @Path("/{id}/{path: .*}")
    public Response proxyPath(@PathParam("id") String id,
                              @PathParam("path") String path,
                              @Context HttpHeaders headers,
                              @Context UriInfo uriInfo) throws IOException {
        return proxy(id, "/" + path, headers.getRequestHeaders(), uriInfo.getRequestUri().getRawQuery());
    }

    // ─────────────── Core proxy logic ───────────────

    private Response proxy(String distributionId,
                           String requestPath,
                           MultivaluedMap<String, String> incomingHeaders,
                           String rawQuery) throws IOException {

        Distribution dist = cfService.getDistribution(distributionId);
        if (!dist.isEnabled()) {
            return Response.status(403)
                    .entity("Distribution " + distributionId + " is disabled.")
                    .build();
        }

        String effectivePath = resolveEffectivePath(requestPath, dist.getDefaultRootObject());
        Origin origin = cfService.resolveOriginForPath(dist, effectivePath);
        if (origin == null) {
            return Response.status(502)
                    .entity("No origin configured for distribution " + distributionId + ".")
                    .build();
        }

        // Prepend the origin's own path prefix
        String originPath = origin.getOriginPath() != null ? origin.getOriginPath() : "";
        String fullPath = originPath + effectivePath;
        if (fullPath.isEmpty()) fullPath = "/";

        LOG.debugv("CloudFront proxy: distribution={0} requestPath={1} → origin={2} fullPath={3}",
                distributionId, requestPath, origin.getDomainName(), fullPath);

        String targetUrl = buildTargetUrl(origin, fullPath, rawQuery);
        return forwardGet(targetUrl, incomingHeaders, origin.getCustomHeaders());
    }

    // ─────────────── URL construction ───────────────

    /**
     * Builds the target URL for the upstream HTTP call.
     *
     * <ul>
     *   <li>S3 origins are mapped to the local emulator.</li>
     *   <li>API Gateway origins are mapped to the local emulator.</li>
     *   <li>All other origins are called directly via HTTP.</li>
     * </ul>
     */
    private String buildTargetUrl(Origin origin, String fullPath, String rawQuery) {
        String domainName = origin.getDomainName();
        String base;

        if (isS3Origin(domainName)) {
            // e.g. my-bucket.s3.amazonaws.com → http://localhost:{port}/my-bucket/path
            String bucket = domainName.substring(0, domainName.indexOf('.'));
            String path = "/" + bucket + (fullPath.startsWith("/") ? fullPath : "/" + fullPath);
            base = baseUrl + path;

        } else if (isApiGatewayOrigin(domainName)) {
            // e.g. abc123.execute-api.us-east-1.amazonaws.com → http://localhost:{port}/execute-api/abc123/path
            String apiId = domainName.substring(0, domainName.indexOf('.'));
            String path = "/execute-api/" + apiId + (fullPath.startsWith("/") ? fullPath : "/" + fullPath);
            base = baseUrl + path;

        } else {
            // Custom / arbitrary HTTP origin
            String scheme = "https-only".equals(origin.getProtocol()) ? "https" : "http";
            base = scheme + "://" + domainName + fullPath;
        }

        return (rawQuery != null && !rawQuery.isEmpty()) ? base + "?" + rawQuery : base;
    }

    /**
     * Resolves the effective request path by applying the {@code DefaultRootObject} when the
     * request targets the bare root ({@code ""} or {@code "/"}).
     *
     * @param requestPath       the raw request path (e.g. {@code "/"} or {@code "/images/cat.jpg"})
     * @param defaultRootObject the distribution's DefaultRootObject (e.g. {@code "index.html"}), may be {@code null}
     * @return the resolved path, never {@code null} and always starting with {@code "/"}
     */
    private static String resolveEffectivePath(String requestPath, String defaultRootObject) {
        // todo: Improve this method
        if (requestPath.isEmpty() || "/".equals(requestPath)) {
            return (defaultRootObject != null && !defaultRootObject.isEmpty())
                    ? "/" + defaultRootObject
                    : "/";
        }
        return requestPath;
    }

    // ─────────────── HTTP forwarding ───────────────

    /**
     * Executes the upstream GET request and maps the response back to a JAX-RS {@link Response}.
     * Hop-by-hop headers are stripped; custom origin headers are injected.
     */
    private Response forwardGet(String targetUrl,
                                MultivaluedMap<String, String> incomingHeaders,
                                List<CustomHeader> customHeaders) {
        try {
            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(targetUrl))
                    .GET();

            // Forward client headers, skipping hop-by-hop entries
            for (Map.Entry<String, List<String>> entry : incomingHeaders.entrySet()) {
                if (HOP_BY_HOP.contains(entry.getKey().toLowerCase())) continue;
                for (String val : entry.getValue()) {
                    reqBuilder.header(entry.getKey(), val);
                }
            }

            // Inject custom headers defined on the CloudFront origin
            for (CustomHeader ch : customHeaders) {
                reqBuilder.header(ch.getHeaderName(), ch.getHeaderValue());
            }

            HttpResponse<byte[]> upstream = HTTP_CLIENT.send(
                    reqBuilder.build(), HttpResponse.BodyHandlers.ofByteArray());

            Response.ResponseBuilder rb = Response.status(upstream.statusCode())
                    .entity(upstream.body());

            // Forward upstream response headers, stripping hop-by-hop and HTTP/2 pseudo-headers
            upstream.headers().map().forEach((name, values) -> {
                String lower = name.toLowerCase();
                if (!lower.startsWith(":") && !HOP_BY_HOP.contains(lower)) {
                    values.forEach(v -> rb.header(name, v));
                }
            });

            return rb.build();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Response.status(502)
                    .entity("CloudFront proxy interrupted: " + e.getMessage())
                    .build();
        } catch (Exception e) {
            LOG.warnv("CloudFront proxy error for target {0}: {1}", targetUrl, e.getMessage());
            return Response.status(502)
                    .entity("Bad Gateway: " + e.getMessage())
                    .build();
        }
    }

    // ─────────────── Origin type detection ───────────────

    private boolean isS3Origin(String domainName) {
        if (domainName == null) return false;
        return domainName.contains(".s3.") || domainName.endsWith(".s3.amazonaws.com");
    }

    private boolean isApiGatewayOrigin(String domainName) {
        if (domainName == null) return false;
        return domainName.contains(".execute-api.");
    }
}
