# CloudFront

**Protocol:** REST XML
**API Version:** `2020-05-31`
**Endpoint:** `http://localhost:4566/2020-05-31/...`

CloudFront is a global service. All distributions are stored globally (not per-region).

## CloudFrontController – Feature Status

### Distributions

| Operation | HTTP | Status | Notes |
|---|---|---|---|
| CreateDistribution | `POST /2020-05-31/distribution` | ✅ Functional | Parses Origins, CacheBehaviors, DefaultRootObject; generates ID, ARN, DomainName |
| ListDistributions | `GET /2020-05-31/distribution` | ✅ Functional | Returns all stored distributions |
| GetDistribution | `GET /2020-05-31/distribution/{Id}` | ✅ Functional | |
| GetDistributionConfig | `GET /2020-05-31/distribution/{Id}/config` | ✅ Functional | |
| UpdateDistribution | `PUT /2020-05-31/distribution/{Id}/config` | ✅ Functional | Updates Comment, Enabled, Origins, DefaultRootObject, CacheBehaviors |
| DeleteDistribution | `DELETE /2020-05-31/distribution/{Id}` | ✅ Functional | |

### Invalidations

| Operation | HTTP | Status | Notes |
|---|---|---|---|
| CreateInvalidation | `POST /2020-05-31/distribution/{Id}/invalidation` | ⚠️ Simplified | Immediately marked as `Completed` – no actual cache invalidation |
| ListInvalidations | `GET /2020-05-31/distribution/{Id}/invalidation` | ✅ Functional | |
| GetInvalidation | `GET /2020-05-31/distribution/{Id}/invalidation/{InvId}` | ✅ Functional | |

### Origins

Origins are configured as part of a distribution. The following fields are supported:

| Field | Status | Notes |
|---|---|---|
| `Id` | ✅ Functional | Used to match `TargetOriginId` in behaviors |
| `DomainName` | ✅ Functional | Determines origin type (S3, API Gateway, custom) and routing target |
| `OriginPath` | ✅ Functional | Prepended to every request path before forwarding |
| `OriginProtocolPolicy` (CustomOriginConfig) | ✅ Functional | Selects http vs. https for custom origins |
| `CustomHeaders` | ✅ Functional | Injected into every upstream request |
| `OriginAccessIdentity` (S3OriginConfig) | ⚠️ Fake | Stored but never used – no request signing is performed |
| `OriginAccessControlId` | ⚠️ Fake | Stored but never used – no SigV4 signing is performed |

### Behaviors

Behaviors are configured as part of a distribution (`DefaultCacheBehavior` and `CacheBehaviors`).

| Field | Status | Notes |
|---|---|---|
| `PathPattern` | ✅ Functional | Evaluated to select the matching origin; wildcards `*` and `**` supported; longest match wins |
| `TargetOriginId` | ✅ Functional | Used to resolve the target origin |
| `ViewerProtocolPolicy` | ⚠️ Fake | Stored but never enforced – no HTTPS redirect or blocking |
| `CachePolicyId` | ⚠️ Fake | Stored but never applied – no caching |
| `DefaultTTL` / `MaxTTL` / `MinTTL` | ⚠️ Fake | Stored but never applied – no caching |

### Cache Policies

| Operation | HTTP | Status | Notes |
|---|---|---|---|
| CreateCachePolicy | `POST /2020-05-31/cache-policy` | ⚠️ Fake | CRUD only – TTL values are stored but never applied |
| ListCachePolicies | `GET /2020-05-31/cache-policy` | ✅ Functional | |
| GetCachePolicy | `GET /2020-05-31/cache-policy/{Id}` | ✅ Functional | |
| UpdateCachePolicy | `PUT /2020-05-31/cache-policy/{Id}` | ⚠️ Fake | CRUD only – TTL values are stored but never applied |
| DeleteCachePolicy | `DELETE /2020-05-31/cache-policy/{Id}` | ✅ Functional | |

### Tags

| Operation | HTTP | Status | Notes |
|---|---|---|---|
| ListTagsForResource | `GET /2020-05-31/tags?Resource=<arn>` | ✅ Functional | |
| TagResource | `POST /2020-05-31/tagging?Operation=Tag&Resource=<arn>` | ✅ Functional | |
| UntagResource | `POST /2020-05-31/tagging?Operation=Untag&Resource=<arn>` | ✅ Functional | |

### Origin Access Controls (OAC)

| Operation | HTTP | Status | Notes |
|---|---|---|---|
| CreateOriginAccessControl | `POST /2020-05-31/origin-access-control` | ✅ Functional | |
| ListOriginAccessControls | `GET /2020-05-31/origin-access-control` | ✅ Functional | |
| GetOriginAccessControl | `GET /2020-05-31/origin-access-control/{Id}` | ✅ Functional | |
| GetOriginAccessControlConfig | `GET /2020-05-31/origin-access-control/{Id}/config` | ✅ Functional | |
| UpdateOriginAccessControl | `PUT /2020-05-31/origin-access-control/{Id}/config` | ✅ Functional | |
| DeleteOriginAccessControl | `DELETE /2020-05-31/origin-access-control/{Id}` | ✅ Functional | |

---

## CloudFrontProxyController – Feature Status

The `CloudFrontProxyController` forwards HTTP GET requests to the configured origin.
Available at `GET /distributions/{id}[/{path}]`.

| Feature | Status | Notes |
|---|---|---|
| Proxy to S3 origin | ✅ Functional | Domain `*.s3.*` → `http://localhost:{port}/{bucket}/{path}` |
| Proxy to API Gateway origin | ✅ Functional | Domain `*.execute-api.*` → `http://localhost:{port}/execute-api/{apiId}/{path}` |
| Proxy to custom origin | ✅ Functional | HTTP forwarding to external domain; protocol (http/https) from `OriginProtocolPolicy` |
| DefaultRootObject | ⚠️ Simplified | Only applied for root path (`/` or empty); no directory fallback |
| OriginPath (path prefix) | ✅ Functional | |
| Custom origin headers | ✅ Functional | Injected into upstream request |
| Hop-by-hop header filtering | ✅ Functional | Standard RFC 7230 headers are not forwarded |
| CacheBehavior / path matching | ⚠️ Partial | Path patterns are evaluated to select the correct origin; TTL and CachePolicyId are ignored |
| Disabled distribution → 403 | ✅ Functional | |
| GET method only | ⚠️ Limited | Only GET is supported; POST/PUT/DELETE are not proxied |
| HTTPS to custom origin | ⚠️ Simplified | HTTPS connection is established, but no certificate pinning |
| Lambda@Edge | ❌ Not implemented | No interceptor support |
| CloudFront Functions | ❌ Not implemented | No interceptor support |
| Query string forwarding | ✅ Functional | Raw query string is forwarded unchanged |
| Response header forwarding | ✅ Functional | Hop-by-hop and HTTP/2 pseudo-headers are filtered |

---

## Input Validation

Most inputs are accepted without validation. The following table shows what is and is not checked:

| Check | Status | Notes |
|---|---|---|
| Duplicate `CallerReference` on CreateDistribution | ✅ Validated | Returns 409 `DistributionAlreadyExists` |
| Distribution existence on Get/Update/Delete | ✅ Validated | Returns 404 `NoSuchDistribution` |
| Invalidation existence on GetInvalidation | ✅ Validated | Returns 404 `NoSuchInvalidation` |
| Duplicate name on CreateCachePolicy | ✅ Validated | Returns 409 `CachePolicyAlreadyExists` |
| CachePolicy existence on Get/Update/Delete | ✅ Validated | Returns 404 `NoSuchCachePolicy` |
| Duplicate name on CreateOriginAccessControl | ✅ Validated | Returns 409 `OriginAccessControlAlreadyExists` |
| OAC existence on Get/Update/Delete | ✅ Validated | Returns 404 `NoSuchOriginAccessControl` |
| ARN existence on tag/untag operations | ✅ Validated | Returns 404 `NoSuchDistribution` |
| `CallerReference` required (non-null/empty) | ❌ Not validated | Null value causes incorrect behaviour |
| `Name` required for CreateCachePolicy | ❌ Not validated | Null value causes `NullPointerException` |
| `Origins` non-empty on CreateDistribution | ❌ Not validated | Distribution without origins can be created |
| `DefaultCacheBehavior` required | ❌ Not validated | Can be omitted without error |
| `TargetOriginId` references existing Origin | ❌ Not validated | Silently falls back to the first origin |
| `minTTL` ≤ `defaultTTL` ≤ `maxTTL` | ❌ Not validated | Invalid TTL ranges are accepted and stored |
| `PathPattern` format | ❌ Not validated | Malformed patterns are accepted |
| `ETag` (If-Match header) on Update/Delete | ❌ Not validated | Any or no ETag is accepted |
| Distribution must be disabled before Delete | ❌ Not validated | AWS requires `Enabled: false` before deletion |
| `DomainName` format on Origin | ❌ Not validated | Arbitrary strings are accepted |
| Request body XML structure | ❌ Not validated | Malformed or empty XML is silently ignored; all fields fall back to defaults |
| Required fields present in body | ❌ Not validated | Missing required elements (e.g. `CallerReference`, `Origins`) yield `null` or empty without an error |
| Element nesting / context | ❌ Not validated | Fields are matched by local tag name only – wrong nesting is silently accepted |
| Unknown / extra elements in body | ❌ Not validated | Unrecognised elements are silently ignored |
| Non-XML body (e.g. plain text, JSON) | ❌ Not validated | Parser catches the exception and returns empty results; request succeeds with defaults |

---

## Logging

### CloudFront Access Logging (AWS Feature)

CloudFront's access logging feature (writing request logs to an S3 bucket) is not implemented. The `Logging` block in the distribution config is not parsed or stored.

| Feature | Status | Notes |
|---|---|---|
| `Logging` field in distribution config | ❌ Not implemented | Not parsed or stored |
| Access log delivery to S3 | ❌ Not implemented | |

### Internal Application Logging

The following internal log statements exist in the implementation:

| Location | Level | Message | Notes |
|---|---|---|---|
| `CloudFrontController.getDistribution` | `DEBUG` | `"hallo getDistribution"` | Development leftover, no useful information |
| `CloudFrontProxyController.proxy` | `DEBUG` | Distribution ID, request path, origin domain, resolved full path | Logged on every proxied request |
| `CloudFrontProxyController.forwardGet` | `WARN` | Target URL and exception message | Logged on proxy errors (502) |
| `CloudFrontProxyFilter.filter` | `DEBUG` | Host, request path, origin domain, resolved full path | Logged on every filtered request |
| `CloudFrontProxyFilter.proxyToCustomOrigin` | `WARN` | Origin domain and exception message | Logged on proxy errors (502) |
| `CloudFrontService` | — | No log statements | CRUD operations are not logged |

---

## Custom Error Pages

Custom error pages (`CustomErrorResponses`) are not implemented. The `CustomErrorResponses` block in the distribution config is not parsed or stored. Error responses from the origin are forwarded to the client unchanged.

| Feature | Status | Notes |
|---|---|---|
| `CustomErrorResponses` in distribution config | ❌ Not implemented | Not parsed or stored |
| Custom response page path per error code | ❌ Not implemented | |
| Custom response HTTP status code mapping | ❌ Not implemented | |
| Error caching TTL (`ErrorCachingMinTTL`) | ❌ Not implemented | |

---

## WAF (Web Application Firewall)

WAF integration is not implemented. The `WebACLId` field is not parsed, stored, or evaluated. Requests are never inspected or blocked based on WAF rules.

| Feature | Status | Notes |
|---|---|---|
| `WebACLId` on distribution | ❌ Not implemented | Field is not parsed or stored |
| WAF rule evaluation on requests | ❌ Not implemented | No request inspection takes place |
| Block / allow / count actions | ❌ Not implemented | |
| AWS Managed Rule Groups | ❌ Not implemented | |

---

## Lambda@Edge and CloudFront Functions

Neither Lambda@Edge nor CloudFront Functions are implemented. The proxy pipeline passes requests directly to the origin without any interceptor support. The `CloudFrontProxyController` is intentionally structured to allow these to be added in the future (see class-level comment), but no event types or trigger hooks exist yet.

| Feature | Status | Notes |
|---|---|---|
| Lambda@Edge – Viewer Request | ❌ Not implemented | |
| Lambda@Edge – Origin Request | ❌ Not implemented | |
| Lambda@Edge – Origin Response | ❌ Not implemented | |
| Lambda@Edge – Viewer Response | ❌ Not implemented | |
| CloudFront Functions – Viewer Request | ❌ Not implemented | |
| CloudFront Functions – Viewer Response | ❌ Not implemented | |

---

## Monitoring

CloudWatch-based monitoring is not implemented. No metrics are collected or published for any CloudFront distribution.

| Feature | Status | Notes |
|---|---|---|
| CloudWatch metrics (requests, error rates, cache hit rate, etc.) | ❌ Not implemented | |
| CloudWatch alarms on distributions | ❌ Not implemented | |
| Real-time logs (Kinesis Data Streams) | ❌ Not implemented | |

---

## Reports & Analytics

No reporting or analytics features are implemented. The emulator does not track request statistics of any kind.

| Feature | Status | Notes |
|---|---|---|
| Cache statistics | ❌ Not implemented | |
| Popular objects | ❌ Not implemented | |
| Top referrers | ❌ Not implemented | |
| Usage (data transfer, requests) | ❌ Not implemented | |
| Viewers (devices, browsers, OS, locations) | ❌ Not implemented | |

---

## Security

### Origin Access

Origin Access Controls (OAC) are partially supported via CRUD API (see [Origin Access Controls](#origin-access-controls-oac) above). However, no actual request signing takes place – the stored `OriginAccessControlId` and `OriginAccessIdentity` are never evaluated when forwarding requests.

| Feature | Status | Notes |
|---|---|---|
| Origin Access Control (OAC) – CRUD | ✅ Functional | Create, list, get, update, delete supported |
| OAC request signing (SigV4) | ❌ Not implemented | Requests to S3 are forwarded unsigned |
| Legacy Origin Access Identity (OAI) | ⚠️ Fake | Stored but never enforced |

### Trust Stores

| Feature | Status | Notes |
|---|---|---|
| Trust store management | ❌ Not implemented | |
| Mutual TLS (mTLS) with custom certificates | ❌ Not implemented | |

### Field-Level Encryption

| Feature | Status | Notes |
|---|---|---|
| Field-level encryption profiles | ❌ Not implemented | |
| Field-level encryption configurations | ❌ Not implemented | |
| Selective field encryption on requests | ❌ Not implemented | |

---

## Key Management

### Public Keys

| Feature | Status | Notes |
|---|---|---|
| CreatePublicKey | ❌ Not implemented | |
| ListPublicKeys | ❌ Not implemented | |
| GetPublicKey / GetPublicKeyConfig | ❌ Not implemented | |
| UpdatePublicKey | ❌ Not implemented | |
| DeletePublicKey | ❌ Not implemented | |

### Key Groups

| Feature | Status | Notes |
|---|---|---|
| CreateKeyGroup | ❌ Not implemented | |
| ListKeyGroups | ❌ Not implemented | |
| GetKeyGroup / GetKeyGroupConfig | ❌ Not implemented | |
| UpdateKeyGroup | ❌ Not implemented | |
| DeleteKeyGroup | ❌ Not implemented | |

---

## Supported Operations

## Configuration

```yaml
floci:
  services:
    cloudfront:
      enabled: true
```

## Examples

### AWS CLI

```bash
export AWS_ENDPOINT=http://localhost:4566
export AWS_DEFAULT_REGION=us-east-1
export AWS_ACCESS_KEY_ID=test
export AWS_SECRET_ACCESS_KEY=test

# Create a bucket and upload content to serve via CloudFront
aws s3 mb s3://my-bucket \
  --endpoint-url $AWS_ENDPOINT 

# Create a simple index.html file
echo "Hello from S3 via CloudFront" > index.html

# Upload the file to S3
aws s3 cp index.html s3://my-bucket/index.html \
  --endpoint-url $AWS_ENDPOINT

# Create a distribution config JSON
cat > distribution-config.json << EOF
{
  "CallerReference": "my-distribution-001",
  "Comment": "Simple CloudFront distribution",
  "Enabled": true,
  "Origins": {
    "Quantity": 1,
    "Items": [
      {
        "Id": "my-origin",
        "DomainName": "my-bucket.s3.amazonaws.com",
        "S3OriginConfig": {
          "OriginAccessIdentity": ""
        }
      }
    ]
  },
  "DefaultCacheBehavior": {
    "TargetOriginId": "my-origin",
    "ViewerProtocolPolicy": "redirect-to-https",
    "TrustedSigners": {
      "Enabled": false,
      "Quantity": 0
    },
    "ForwardedValues": {
      "QueryString": false,
      "Cookies": {
        "Forward": "none"
      }
    },
    "MinTTL": 0
  }
}
EOF

# Create the distribution using the config
aws cloudfront create-distribution \                          
  --distribution-config file://distribution-config.json \
  --endpoint-url $AWS_ENDPOINT

# Get the distribution ID
DISTRIBUTION_ID=$(aws cloudfront list-distributions \
  --endpoint-url $AWS_ENDPOINT \
  --query 'DistributionList.Items[0].Id' \
  --output text)
  
curl $AWS_ENDPOINT/distributions/$DISTRIBUTION_ID/index.html
```

### AWS SDK (Java)

```java
CloudFrontClient client = CloudFrontClient.builder()
    .endpointOverride(URI.create("http://localhost:4566"))
    .region(Region.US_EAST_1)
    .credentialsProvider(StaticCredentialsProvider.create(
        AwsBasicCredentials.create("test", "test")))
    .build();

CreateDistributionResponse response = client.createDistribution(r -> r
    .distributionConfig(cfg -> cfg
        .callerReference("my-ref-1")
        .comment("My distribution")
        .enabled(true)
        .origins(o -> o
            .quantity(1)
            .items(origin -> origin
                .id("myOrigin")
                .domainName("mybucket.s3.amazonaws.com")
                .s3OriginConfig(s3 -> s3.originAccessIdentity(""))))
        .defaultCacheBehavior(dcb -> dcb
            .targetOriginId("myOrigin")
            .viewerProtocolPolicy(ViewerProtocolPolicy.REDIRECT_TO_HTTPS)
            .forwardedValues(fv -> fv
                .queryString(false)
                .cookies(c -> c.forward(ItemSelection.NONE)))
            .minTTL(0L))));
```

## Notes

- Distributions are immediately set to `Deployed` status (no `InProgress` delay).
- Invalidations are immediately set to `Completed` status. No actual cache invalidation takes place.
- ETags are generated and returned but not strictly validated on updates/deletes.
- **No caching behaviour is implemented.** Every request is proxied directly to the origin. TTL values and Cache Policies are persisted via the API but have no effect on request handling.
