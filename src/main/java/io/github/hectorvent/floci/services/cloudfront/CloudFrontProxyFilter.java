package io.github.hectorvent.floci.services.cloudfront;

import io.github.hectorvent.floci.services.cloudfront.model.CustomHeader;
import io.github.hectorvent.floci.services.cloudfront.model.Distribution;
import io.github.hectorvent.floci.services.cloudfront.model.Origin;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.PreMatching;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriBuilder;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Pre-matching filter that implements CloudFront traffic proxying.
 *
 * <p>Intercepts any request whose {@code Host} header matches a registered
 * CloudFront distribution domain (e.g. {@code abcd1234.cloudfront.net} or
 * {@code abcd1234.cloudfront.localhost:4566}) and routes it to the correct
 * origin by applying CloudFront's path-pattern matching rules.
 *
 * <h2>Routing strategy</h2>
 * <ul>
 *   <li><b>S3 origins</b> ({@code *.s3.amazonaws.com} / {@code *.s3.*}) –
 *       URI rewrite to {@code /bucket/path} so the internal S3 controller
 *       handles the request without an extra round-trip.</li>
 *   <li><b>API Gateway origins</b> ({@code *.execute-api.*}) –
 *       URI rewrite to {@code /execute-api/<apiId><originPath+requestPath>}.</li>
 *   <li><b>Custom / arbitrary origins</b> –
 *       Forwarded via {@link java.net.http.HttpClient}.
 *       Note: this performs blocking I/O on the calling thread, which is
 *       acceptable for local development emulators but should not be used
 *       under high concurrency.</li>
 * </ul>
 *
 * <h2>Additional features</h2>
 * <ul>
 *   <li>Custom origin headers are injected into every forwarded request.</li>
 *   <li>The {@code DefaultRootObject} is applied when the request path is
 *       {@code /}.</li>
 *   <li>Disabled distributions return {@code 403 Forbidden}.</li>
 * </ul>
 */
@Provider
@PreMatching
@Priority(10)
public class CloudFrontProxyFilter implements ContainerRequestFilter {

    private static final Logger LOG = Logger.getLogger(CloudFrontProxyFilter.class);

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private final CloudFrontService cfService;

    @Inject
    public CloudFrontProxyFilter(CloudFrontService cfService) {
        this.cfService = cfService;
    }

    // ─────────────── Filter entry point ───────────────

    @Override
    public void filter(ContainerRequestContext ctx) throws IOException {
        String host = ctx.getHeaderString("Host");
        if (host == null || !host.contains(".cloudfront.")) return;

        Optional<Distribution> opt = cfService.findByDomainName(host);
        if (opt.isEmpty()) {
            // Unknown CloudFront domain — let the request fall through so that
            // CloudFront management API calls (which don't use *.cloudfront.net
            // as hostname) are not accidentally blocked.
            return;
        }

        Distribution dist = opt.get();

        if (!dist.isEnabled()) {
            ctx.abortWith(Response.status(403)
                    .entity("Distribution " + dist.getId() + " is disabled.")
                    .build());
            return;
        }

        URI requestUri = ctx.getUriInfo().getRequestUri();
        String requestPath = requestUri.getRawPath();
        if (requestPath == null || requestPath.isEmpty()) requestPath = "/";

        Origin origin = cfService.resolveOriginForPath(dist, requestPath);
        if (origin == null) {
            ctx.abortWith(Response.status(502)
                    .entity("No origin configured for this distribution.")
                    .build());
            return;
        }

        // Apply origin path prefix
        String originPath = origin.getOriginPath() != null ? origin.getOriginPath() : "";

        // Apply DefaultRootObject only for the bare root request
        String effectivePath = requestPath;
        if (("/".equals(requestPath) || requestPath.isEmpty())
                && dist.getDefaultRootObject() != null
                && !dist.getDefaultRootObject().isEmpty()) {
            effectivePath = "/" + dist.getDefaultRootObject();
        }

        String fullPath = originPath + effectivePath;
        if (fullPath.isEmpty()) fullPath = "/";

        // Inject custom headers before any routing decision
        for (CustomHeader ch : origin.getCustomHeaders()) {
            ctx.getHeaders().add(ch.getHeaderName(), ch.getHeaderValue());
        }

        String domainName = origin.getDomainName();
        LOG.debugv("CloudFront proxy: host={0} path={1} → origin={2} fullPath={3}",
                host, requestPath, domainName, fullPath);

        if (isS3Origin(domainName)) {
            rewriteToS3(ctx, requestUri, domainName, fullPath);
        } else if (isApiGatewayOrigin(domainName)) {
            rewriteToApiGateway(ctx, requestUri, domainName, fullPath);
        } else {
            proxyToCustomOrigin(ctx, origin, requestUri, fullPath);
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

    // ─────────────── Internal URI rewrites ───────────────

    /**
     * Rewrites the request URI so the internal S3 controller handles it.
     * {@code bucket.s3.amazonaws.com} → path {@code /bucket/fullPath}.
     */
    private void rewriteToS3(ContainerRequestContext ctx, URI originalUri,
                              String domainName, String fullPath) {
        String bucket = domainName.substring(0, domainName.indexOf('.'));
        String newPath = "/" + bucket + (fullPath.startsWith("/") ? fullPath : "/" + fullPath);
        URI newUri = UriBuilder.fromUri(originalUri)
                .replacePath(newPath)
                .build();
        ctx.setRequestUri(newUri);
    }

    /**
     * Rewrites the request URI so the internal API Gateway execute controller
     * handles it.  {@code apiId.execute-api.region.amazonaws.com} with
     * fullPath {@code /stage/resource} → {@code /execute-api/apiId/stage/resource}.
     */
    private void rewriteToApiGateway(ContainerRequestContext ctx, URI originalUri,
                                      String domainName, String fullPath) {
        String apiId = domainName.substring(0, domainName.indexOf('.'));
        String newPath = "/execute-api/" + apiId + (fullPath.startsWith("/") ? fullPath : "/" + fullPath);
        URI newUri = UriBuilder.fromUri(originalUri)
                .replacePath(newPath)
                .build();
        ctx.setRequestUri(newUri);
    }

    // ─────────────── External HTTP proxy ───────────────

    /**
     * Forwards the request to an arbitrary external origin using
     * {@link java.net.http.HttpClient} and responds via
     * {@link ContainerRequestContext#abortWith(Response)}.
     */
    private void proxyToCustomOrigin(ContainerRequestContext ctx, Origin origin,
                                      URI originalUri, String fullPath) throws IOException {
        try {
            String scheme = "https-only".equals(origin.getProtocol()) ? "https" : "http";
            String targetUrl = scheme + "://" + origin.getDomainName() + fullPath;
            String query = originalUri.getRawQuery();
            if (query != null && !query.isEmpty()) {
                targetUrl += "?" + query;
            }

            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(targetUrl));

            // Forward request headers (skip hop-by-hop headers)
            MultivaluedMap<String, String> incomingHeaders = ctx.getHeaders();
            for (Map.Entry<String, List<String>> entry : incomingHeaders.entrySet()) {
                String headerName = entry.getKey().toLowerCase();
                if (headerName.equals("host")
                        || headerName.equals("content-length")
                        || headerName.equals("transfer-encoding")
                        || headerName.equals("connection")) {
                    continue;
                }
                for (String val : entry.getValue()) {
                    reqBuilder.header(entry.getKey(), val);
                }
            }

            // Read body
            byte[] bodyBytes = ctx.getEntityStream().readAllBytes();
            HttpRequest.BodyPublisher publisher = bodyBytes.length > 0
                    ? HttpRequest.BodyPublishers.ofByteArray(bodyBytes)
                    : HttpRequest.BodyPublishers.noBody();
            reqBuilder.method(ctx.getMethod(), publisher);

            HttpResponse<byte[]> upstream = HTTP_CLIENT.send(
                    reqBuilder.build(), HttpResponse.BodyHandlers.ofByteArray());

            Response.ResponseBuilder rb = Response.status(upstream.statusCode())
                    .entity(upstream.body());
            upstream.headers().map().forEach((k, vals) -> {
                String lk = k.toLowerCase();
                if (!lk.equals("transfer-encoding") && !lk.equals("connection")) {
                    vals.forEach(v -> rb.header(k, v));
                }
            });

            ctx.abortWith(rb.build());

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            ctx.abortWith(Response.status(502)
                    .entity("CloudFront proxy interrupted: " + e.getMessage())
                    .build());
        } catch (Exception e) {
            LOG.warnv("CloudFront proxy error for origin {0}: {1}",
                    origin.getDomainName(), e.getMessage());
            ctx.abortWith(Response.status(502)
                    .entity("Bad Gateway: " + e.getMessage())
                    .build());
        }
    }
}
