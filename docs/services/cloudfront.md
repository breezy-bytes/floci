# CloudFront

**Protocol:** REST XML
**API Version:** `2020-05-31`
**Endpoint:** `http://localhost:4566/2020-05-31/...`

CloudFront is a global service. All distributions are stored globally (not per-region).

## Supported Operations

| Category | Operation | HTTP |
|---|---|---|
| **Distributions** | CreateDistribution | `POST /2020-05-31/distribution` |
| | ListDistributions | `GET /2020-05-31/distribution` |
| | GetDistribution | `GET /2020-05-31/distribution/{Id}` |
| | GetDistributionConfig | `GET /2020-05-31/distribution/{Id}/config` |
| | UpdateDistribution | `PUT /2020-05-31/distribution/{Id}/config` |
| | DeleteDistribution | `DELETE /2020-05-31/distribution/{Id}` |
| **Invalidations** | CreateInvalidation | `POST /2020-05-31/distribution/{Id}/invalidation` |
| | ListInvalidations | `GET /2020-05-31/distribution/{Id}/invalidation` |
| | GetInvalidation | `GET /2020-05-31/distribution/{Id}/invalidation/{InvId}` |
| **Tags** | ListTagsForResource | `GET /2020-05-31/tags?Resource=<arn>` |
| | TagResource / UntagResource | `POST /2020-05-31/tagging?Operation=Tag&Resource=<arn>` |

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
- Invalidations are immediately set to `Completed` status.
- ETags are generated and returned but not strictly validated on updates/deletes.
