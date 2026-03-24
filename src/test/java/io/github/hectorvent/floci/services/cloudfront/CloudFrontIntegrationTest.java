package io.github.hectorvent.floci.services.cloudfront;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CloudFrontIntegrationTest {

    private static String distributionId;
    private static String distributionArn;
    private static String distributionDomain;
    private static String invalidationId;
    private static String cachePolicyId;
    private static String oacId;

    // ─────────────── Distributions ───────────────

    @Test
    @Order(1)
    void createDistribution() {
        String body = """
                <DistributionConfig>
                  <CallerReference>test-ref-1</CallerReference>
                  <Comment>Test distribution</Comment>
                  <Enabled>true</Enabled>
                  <DefaultRootObject>index.html</DefaultRootObject>
                  <Origins>
                    <Quantity>1</Quantity>
                    <Items>
                      <Origin>
                        <Id>origin-1</Id>
                        <DomainName>example.s3.amazonaws.com</DomainName>
                      </Origin>
                    </Items>
                  </Origins>
                  <DefaultCacheBehavior>
                    <TargetOriginId>origin-1</TargetOriginId>
                    <ViewerProtocolPolicy>redirect-to-https</ViewerProtocolPolicy>
                    <DefaultTTL>86400</DefaultTTL>
                    <MaxTTL>31536000</MaxTTL>
                    <MinTTL>0</MinTTL>
                  </DefaultCacheBehavior>
                </DistributionConfig>
                """;

        String response = given()
                .contentType("text/xml")
                .body(body)
            .when()
                .post("/2020-05-31/distribution")
            .then()
                .statusCode(201)
                .header("ETag", notNullValue())
                .header("Location", containsString("/2020-05-31/distribution/"))
                .body(containsString("<Status>Deployed</Status>"))
                .body(containsString("<DomainName>"))
                .body(containsString(".cloudfront.net"))
                .body(containsString("<CallerReference>test-ref-1</CallerReference>"))
                .body(containsString("<Comment>Test distribution</Comment>"))
                .body(containsString("<Enabled>true</Enabled>"))
                .extract().body().asString();

        distributionId = extractTag(response, "Id");
        distributionArn = extractTag(response, "ARN");
        distributionDomain = extractTag(response, "DomainName");
    }

    @Test
    @Order(2)
    void createDistributionDuplicateCallerReferenceFails() {
        String body = """
                <DistributionConfig>
                  <CallerReference>test-ref-1</CallerReference>
                  <Comment>Duplicate</Comment>
                  <Enabled>true</Enabled>
                  <Origins>
                    <Quantity>1</Quantity>
                    <Items>
                      <Origin>
                        <Id>o1</Id>
                        <DomainName>other.com</DomainName>
                      </Origin>
                    </Items>
                  </Origins>
                </DistributionConfig>
                """;

        given()
                .contentType("text/xml")
                .body(body)
            .when()
                .post("/2020-05-31/distribution")
            .then()
                .statusCode(409)
                .body(containsString("DistributionAlreadyExists"));
    }

    @Test
    @Order(3)
    void getDistribution() {
        given()
            .when()
                .get("/2020-05-31/distribution/" + distributionId)
            .then()
                .statusCode(200)
                .header("ETag", notNullValue())
                .body(containsString("<Id>" + distributionId + "</Id>"))
                .body(containsString("<Status>Deployed</Status>"))
                .body(containsString("example.s3.amazonaws.com"));
    }

    @Test
    @Order(4)
    void getDistributionNotFound() {
        given()
            .when()
                .get("/2020-05-31/distribution/NONEXISTENT0001")
            .then()
                .statusCode(404)
                .body(containsString("NoSuchDistribution"));
    }

    @Test
    @Order(5)
    void getDistributionConfig() {
        given()
            .when()
                .get("/2020-05-31/distribution/" + distributionId + "/config")
            .then()
                .statusCode(200)
                .header("ETag", notNullValue())
                .body(containsString("<CallerReference>test-ref-1</CallerReference>"))
                .body(containsString("<DefaultRootObject>index.html</DefaultRootObject>"));
    }

    @Test
    @Order(6)
    void listDistributions() {
        given()
            .when()
                .get("/2020-05-31/distribution")
            .then()
                .statusCode(200)
                .body(containsString(distributionId))
                .body(containsString("<IsTruncated>false</IsTruncated>"));
    }

    @Test
    @Order(7)
    void updateDistribution() {
        String body = """
                <DistributionConfig>
                  <Comment>Updated comment</Comment>
                  <Enabled>false</Enabled>
                  <DefaultRootObject>home.html</DefaultRootObject>
                  <Origins>
                    <Quantity>1</Quantity>
                    <Items>
                      <Origin>
                        <Id>origin-2</Id>
                        <DomainName>updated.s3.amazonaws.com</DomainName>
                      </Origin>
                    </Items>
                  </Origins>
                  <DefaultCacheBehavior>
                    <TargetOriginId>origin-2</TargetOriginId>
                    <ViewerProtocolPolicy>https-only</ViewerProtocolPolicy>
                  </DefaultCacheBehavior>
                </DistributionConfig>
                """;

        given()
                .contentType("text/xml")
                .body(body)
            .when()
                .put("/2020-05-31/distribution/" + distributionId + "/config")
            .then()
                .statusCode(200)
                .header("ETag", notNullValue())
                .body(containsString("<Comment>Updated comment</Comment>"))
                .body(containsString("<Enabled>false</Enabled>"))
                .body(containsString("updated.s3.amazonaws.com"));
    }

    // ─────────────── Invalidations ───────────────

    @Test
    @Order(10)
    void createInvalidation() {
        String body = """
                <InvalidationBatch>
                  <CallerReference>inv-ref-1</CallerReference>
                  <Paths>
                    <Quantity>2</Quantity>
                    <Items>
                      <Path>/images/*</Path>
                      <Path>/index.html</Path>
                    </Items>
                  </Paths>
                </InvalidationBatch>
                """;

        String response = given()
                .contentType("text/xml")
                .body(body)
            .when()
                .post("/2020-05-31/distribution/" + distributionId + "/invalidation")
            .then()
                .statusCode(201)
                .header("Location", containsString("/invalidation/"))
                .body(containsString("<Status>Completed</Status>"))
                .body(containsString("<CallerReference>inv-ref-1</CallerReference>"))
                .body(containsString("<Path>/images/*</Path>"))
                .body(containsString("<Path>/index.html</Path>"))
                .extract().body().asString();

        invalidationId = extractTag(response, "Id");
    }

    @Test
    @Order(11)
    void createInvalidationForNonExistentDistributionFails() {
        String body = """
                <InvalidationBatch>
                  <CallerReference>inv-ref-2</CallerReference>
                  <Paths><Quantity>0</Quantity></Paths>
                </InvalidationBatch>
                """;

        given()
                .contentType("text/xml")
                .body(body)
            .when()
                .post("/2020-05-31/distribution/NONEXISTENT0001/invalidation")
            .then()
                .statusCode(404)
                .body(containsString("NoSuchDistribution"));
    }

    @Test
    @Order(12)
    void listInvalidations() {
        given()
            .when()
                .get("/2020-05-31/distribution/" + distributionId + "/invalidation")
            .then()
                .statusCode(200)
                .body(containsString(invalidationId))
                .body(containsString("<IsTruncated>false</IsTruncated>"));
    }

    @Test
    @Order(13)
    void getInvalidation() {
        given()
            .when()
                .get("/2020-05-31/distribution/" + distributionId + "/invalidation/" + invalidationId)
            .then()
                .statusCode(200)
                .body(containsString("<Id>" + invalidationId + "</Id>"))
                .body(containsString("<Status>Completed</Status>"));
    }

    @Test
    @Order(14)
    void getInvalidationNotFound() {
        given()
            .when()
                .get("/2020-05-31/distribution/" + distributionId + "/invalidation/NOSUCHINV0001")
            .then()
                .statusCode(404)
                .body(containsString("NoSuchInvalidation"));
    }

    // ─────────────── Cache Policies ───────────────

    @Test
    @Order(20)
    void createCachePolicy() {
        String body = """
                <CachePolicyConfig>
                  <Name>my-cache-policy</Name>
                  <Comment>Test cache policy</Comment>
                  <DefaultTTL>3600</DefaultTTL>
                  <MaxTTL>86400</MaxTTL>
                  <MinTTL>60</MinTTL>
                </CachePolicyConfig>
                """;

        String response = given()
                .contentType("text/xml")
                .body(body)
            .when()
                .post("/2020-05-31/cache-policy")
            .then()
                .statusCode(201)
                .header("ETag", notNullValue())
                .header("Location", containsString("/2020-05-31/cache-policy/"))
                .body(containsString("<Name>my-cache-policy</Name>"))
                .body(containsString("<Comment>Test cache policy</Comment>"))
                .body(containsString("<DefaultTTL>3600</DefaultTTL>"))
                .body(containsString("<MaxTTL>86400</MaxTTL>"))
                .body(containsString("<MinTTL>60</MinTTL>"))
                .extract().body().asString();

        cachePolicyId = extractTag(response, "Id");
    }

    @Test
    @Order(21)
    void createCachePolicyDuplicateNameFails() {
        String body = """
                <CachePolicyConfig>
                  <Name>my-cache-policy</Name>
                  <Comment>Duplicate</Comment>
                  <DefaultTTL>100</DefaultTTL>
                  <MaxTTL>200</MaxTTL>
                  <MinTTL>0</MinTTL>
                </CachePolicyConfig>
                """;

        given()
                .contentType("text/xml")
                .body(body)
            .when()
                .post("/2020-05-31/cache-policy")
            .then()
                .statusCode(409)
                .body(containsString("CachePolicyAlreadyExists"));
    }

    @Test
    @Order(22)
    void getCachePolicy() {
        given()
            .when()
                .get("/2020-05-31/cache-policy/" + cachePolicyId)
            .then()
                .statusCode(200)
                .header("ETag", notNullValue())
                .body(containsString("<Id>" + cachePolicyId + "</Id>"))
                .body(containsString("<Name>my-cache-policy</Name>"));
    }

    @Test
    @Order(23)
    void getCachePolicyNotFound() {
        given()
            .when()
                .get("/2020-05-31/cache-policy/00000000-0000-0000-0000-000000000000")
            .then()
                .statusCode(404)
                .body(containsString("NoSuchCachePolicy"));
    }

    @Test
    @Order(24)
    void listCachePolicies() {
        given()
            .when()
                .get("/2020-05-31/cache-policy")
            .then()
                .statusCode(200)
                .body(containsString(cachePolicyId))
                .body(containsString("<Name>my-cache-policy</Name>"));
    }

    @Test
    @Order(25)
    void updateCachePolicy() {
        String body = """
                <CachePolicyConfig>
                  <Name>my-cache-policy-updated</Name>
                  <Comment>Updated comment</Comment>
                  <DefaultTTL>7200</DefaultTTL>
                  <MaxTTL>172800</MaxTTL>
                  <MinTTL>120</MinTTL>
                </CachePolicyConfig>
                """;

        given()
                .contentType("text/xml")
                .body(body)
            .when()
                .put("/2020-05-31/cache-policy/" + cachePolicyId)
            .then()
                .statusCode(200)
                .header("ETag", notNullValue())
                .body(containsString("<Name>my-cache-policy-updated</Name>"))
                .body(containsString("<DefaultTTL>7200</DefaultTTL>"))
                .body(containsString("<MinTTL>120</MinTTL>"));
    }

    @Test
    @Order(26)
    void deleteCachePolicy() {
        given()
            .when()
                .delete("/2020-05-31/cache-policy/" + cachePolicyId)
            .then()
                .statusCode(204);

        given()
            .when()
                .get("/2020-05-31/cache-policy/" + cachePolicyId)
            .then()
                .statusCode(404);
    }

    // ─────────────── Tags ───────────────

    @Test
    @Order(30)
    void tagDistribution() {
        String body = """
                <Tags>
                  <Items>
                    <Tag><Key>Environment</Key><Value>production</Value></Tag>
                    <Tag><Key>Project</Key><Value>floci</Value></Tag>
                  </Items>
                </Tags>
                """;

        given()
                .contentType("text/xml")
                .body(body)
                .queryParam("Resource", distributionArn)
                .queryParam("Operation", "Tag")
            .when()
                .post("/2020-05-31/tagging")
            .then()
                .statusCode(204);
    }

    @Test
    @Order(31)
    void listTagsForDistribution() {
        given()
                .queryParam("Resource", distributionArn)
            .when()
                .get("/2020-05-31/tags")
            .then()
                .statusCode(200)
                .body(containsString("<Key>Environment</Key>"))
                .body(containsString("<Value>production</Value>"))
                .body(containsString("<Key>Project</Key>"))
                .body(containsString("<Value>floci</Value>"));
    }

    @Test
    @Order(32)
    void untagDistribution() {
        String body = """
                <TagKeys>
                  <Items>
                    <Key>Project</Key>
                  </Items>
                </TagKeys>
                """;

        given()
                .contentType("text/xml")
                .body(body)
                .queryParam("Resource", distributionArn)
                .queryParam("Operation", "Untag")
            .when()
                .post("/2020-05-31/tagging")
            .then()
                .statusCode(204);

        given()
                .queryParam("Resource", distributionArn)
            .when()
                .get("/2020-05-31/tags")
            .then()
                .statusCode(200)
                .body(containsString("Environment"))
                .body(not(containsString("<Key>Project</Key>")));
    }

    @Test
    @Order(33)
    void listTagsForNonExistentDistributionFails() {
        given()
                .queryParam("Resource", "arn:aws:cloudfront::000000000000:distribution/NONEXISTENT")
            .when()
                .get("/2020-05-31/tags")
            .then()
                .statusCode(404)
                .body(containsString("NoSuchDistribution"));
    }

    // ─────────────── Origin Access Controls ───────────────

    @Test
    @Order(50)
    void createOriginAccessControl() {
        String body = """
                <OriginAccessControlConfig>
                  <Name>my-oac</Name>
                  <Description>Test OAC for S3</Description>
                  <SigningBehavior>always</SigningBehavior>
                  <SigningProtocol>sigv4</SigningProtocol>
                  <OriginAccessControlOriginType>s3</OriginAccessControlOriginType>
                </OriginAccessControlConfig>
                """;

        String response = given()
                .contentType("text/xml")
                .body(body)
            .when()
                .post("/2020-05-31/origin-access-control")
            .then()
                .statusCode(201)
                .header("ETag", notNullValue())
                .header("Location", containsString("/2020-05-31/origin-access-control/"))
                .body(containsString("<Name>my-oac</Name>"))
                .body(containsString("<SigningBehavior>always</SigningBehavior>"))
                .body(containsString("<SigningProtocol>sigv4</SigningProtocol>"))
                .body(containsString("<OriginAccessControlOriginType>s3</OriginAccessControlOriginType>"))
                .extract().body().asString();

        oacId = extractTag(response, "Id");
        assertFalse(oacId.isEmpty(), "OAC id must not be empty");
    }

    @Test
    @Order(51)
    void createOriginAccessControlDuplicateNameFails() {
        String body = """
                <OriginAccessControlConfig>
                  <Name>my-oac</Name>
                  <Description>Duplicate</Description>
                  <SigningBehavior>always</SigningBehavior>
                  <SigningProtocol>sigv4</SigningProtocol>
                  <OriginAccessControlOriginType>s3</OriginAccessControlOriginType>
                </OriginAccessControlConfig>
                """;

        given()
                .contentType("text/xml")
                .body(body)
            .when()
                .post("/2020-05-31/origin-access-control")
            .then()
                .statusCode(409)
                .body(containsString("OriginAccessControlAlreadyExists"));
    }

    @Test
    @Order(52)
    void getOriginAccessControl() {
        given()
            .when()
                .get("/2020-05-31/origin-access-control/" + oacId)
            .then()
                .statusCode(200)
                .header("ETag", notNullValue())
                .body(containsString("<Id>" + oacId + "</Id>"))
                .body(containsString("<Name>my-oac</Name>"));
    }

    @Test
    @Order(53)
    void getOriginAccessControlConfig() {
        given()
            .when()
                .get("/2020-05-31/origin-access-control/" + oacId + "/config")
            .then()
                .statusCode(200)
                .body(containsString("<Name>my-oac</Name>"))
                .body(containsString("<Description>Test OAC for S3</Description>"));
    }

    @Test
    @Order(54)
    void listOriginAccessControls() {
        given()
            .when()
                .get("/2020-05-31/origin-access-control")
            .then()
                .statusCode(200)
                .body(containsString(oacId))
                .body(containsString("<IsTruncated>false</IsTruncated>"));
    }

    @Test
    @Order(55)
    void updateOriginAccessControl() {
        String body = """
                <OriginAccessControlConfig>
                  <Name>my-oac-updated</Name>
                  <Description>Updated</Description>
                  <SigningBehavior>no-override</SigningBehavior>
                  <SigningProtocol>sigv4</SigningProtocol>
                  <OriginAccessControlOriginType>s3</OriginAccessControlOriginType>
                </OriginAccessControlConfig>
                """;

        given()
                .contentType("text/xml")
                .body(body)
            .when()
                .put("/2020-05-31/origin-access-control/" + oacId + "/config")
            .then()
                .statusCode(200)
                .header("ETag", notNullValue())
                .body(containsString("<Name>my-oac-updated</Name>"))
                .body(containsString("<SigningBehavior>no-override</SigningBehavior>"));
    }

    @Test
    @Order(56)
    void getOriginAccessControlNotFound() {
        given()
            .when()
                .get("/2020-05-31/origin-access-control/00000000-0000-0000-0000-000000000000")
            .then()
                .statusCode(404)
                .body(containsString("NoSuchOriginAccessControl"));
    }

    @Test
    @Order(57)
    void deleteOriginAccessControl() {
        given()
            .when()
                .delete("/2020-05-31/origin-access-control/" + oacId)
            .then()
                .statusCode(204);

        given()
            .when()
                .get("/2020-05-31/origin-access-control/" + oacId)
            .then()
                .statusCode(404);
    }

    // ─────────────── Custom Headers ───────────────

    @Test
    @Order(60)
    void distributionWithCustomHeadersAndOacIsStoredAndReturned() {
        String body = """
                <DistributionConfig>
                  <CallerReference>custom-headers-test</CallerReference>
                  <Comment>Custom headers test</Comment>
                  <Enabled>true</Enabled>
                  <Origins>
                    <Quantity>1</Quantity>
                    <Items>
                      <Origin>
                        <Id>s3-with-headers</Id>
                        <DomainName>my-bucket.s3.amazonaws.com</DomainName>
                        <OriginAccessControlId>fake-oac-id</OriginAccessControlId>
                        <CustomHeaders>
                          <Quantity>2</Quantity>
                          <Items>
                            <OriginCustomHeader>
                              <HeaderName>X-Custom-Token</HeaderName>
                              <HeaderValue>secret123</HeaderValue>
                            </OriginCustomHeader>
                            <OriginCustomHeader>
                              <HeaderName>X-Environment</HeaderName>
                              <HeaderValue>staging</HeaderValue>
                            </OriginCustomHeader>
                          </Items>
                        </CustomHeaders>
                      </Origin>
                    </Items>
                  </Origins>
                  <DefaultCacheBehavior>
                    <TargetOriginId>s3-with-headers</TargetOriginId>
                    <ViewerProtocolPolicy>redirect-to-https</ViewerProtocolPolicy>
                  </DefaultCacheBehavior>
                </DistributionConfig>
                """;

        String response = given()
                .contentType("text/xml")
                .body(body)
            .when()
                .post("/2020-05-31/distribution")
            .then()
                .statusCode(201)
                .body(containsString("my-bucket.s3.amazonaws.com"))
                .body(containsString("<OriginAccessControlId>fake-oac-id</OriginAccessControlId>"))
                .body(containsString("<HeaderName>X-Custom-Token</HeaderName>"))
                .body(containsString("<HeaderValue>secret123</HeaderValue>"))
                .body(containsString("<HeaderName>X-Environment</HeaderName>"))
                .body(containsString("<HeaderValue>staging</HeaderValue>"))
                .extract().body().asString();

        // Cleanup
        String id = extractTag(response, "Id");
        given().delete("/2020-05-31/distribution/" + id).then().statusCode(204);
    }

    // ─────────────── Proxy routing ───────────────

    @Test
    @Order(70)
    void proxyToS3OriginRewritesUri() {
        // Create a bucket + object in the embedded S3 emulator
        given()
            .when()
                .put("/probe-bucket")
            .then()
                .statusCode(anyOf(is(200), is(204)));

        given()
                .contentType("text/plain")
                .body("hello from s3")
            .when()
                .put("/probe-bucket/hello.txt")
            .then()
                .statusCode(anyOf(is(200), is(204)));

        // Create a distribution pointing at the local S3 bucket
        String cfBody = """
                <DistributionConfig>
                  <CallerReference>proxy-s3-test</CallerReference>
                  <Comment>S3 proxy test</Comment>
                  <Enabled>true</Enabled>
                  <DefaultRootObject>hello.txt</DefaultRootObject>
                  <Origins>
                    <Quantity>1</Quantity>
                    <Items>
                      <Origin>
                        <Id>s3-probe</Id>
                        <DomainName>probe-bucket.s3.amazonaws.com</DomainName>
                      </Origin>
                    </Items>
                  </Origins>
                  <DefaultCacheBehavior>
                    <TargetOriginId>s3-probe</TargetOriginId>
                    <ViewerProtocolPolicy>allow-all</ViewerProtocolPolicy>
                  </DefaultCacheBehavior>
                </DistributionConfig>
                """;

        String cfResponse = given()
                .contentType("text/xml")
                .body(cfBody)
            .when()
                .post("/2020-05-31/distribution")
            .then()
                .statusCode(201)
                .extract().body().asString();

        String cfId     = extractTag(cfResponse, "Id");
        String cfDomain = extractTag(cfResponse, "DomainName");

        // GET /hello.txt via the CloudFront distribution domain
        given()
                .header("Host", cfDomain)
            .when()
                .get("/hello.txt")
            .then()
                .statusCode(200)
                .body(equalTo("hello from s3"));

        // GET / — DefaultRootObject should serve hello.txt
        given()
                .header("Host", cfDomain)
            .when()
                .get("/")
            .then()
                .statusCode(200)
                .body(equalTo("hello from s3"));

        // Cleanup
        given().delete("/2020-05-31/distribution/" + cfId).then().statusCode(204);
    }

    @Test
    @Order(71)
    void proxyDisabledDistributionReturns403() {
        String cfBody = """
                <DistributionConfig>
                  <CallerReference>proxy-disabled-test</CallerReference>
                  <Enabled>false</Enabled>
                  <Origins>
                    <Quantity>1</Quantity>
                    <Items>
                      <Origin>
                        <Id>o1</Id>
                        <DomainName>nowhere.s3.amazonaws.com</DomainName>
                      </Origin>
                    </Items>
                  </Origins>
                  <DefaultCacheBehavior>
                    <TargetOriginId>o1</TargetOriginId>
                    <ViewerProtocolPolicy>allow-all</ViewerProtocolPolicy>
                  </DefaultCacheBehavior>
                </DistributionConfig>
                """;

        String cfResponse = given()
                .contentType("text/xml")
                .body(cfBody)
            .when()
                .post("/2020-05-31/distribution")
            .then()
                .statusCode(201)
                .extract().body().asString();

        String cfId     = extractTag(cfResponse, "Id");
        String cfDomain = extractTag(cfResponse, "DomainName");

        given()
                .header("Host", cfDomain)
            .when()
                .get("/index.html")
            .then()
                .statusCode(403);

        // Cleanup
        given().delete("/2020-05-31/distribution/" + cfId).then().statusCode(204);
    }

    @Test
    @Order(72)
    void proxyWithMultipleOriginsAndCacheBehaviors() {
        // Set up S3 content
        given().put("/api-bucket").then().statusCode(anyOf(is(200), is(204)));
        given().contentType("text/plain").body("static content")
               .put("/api-bucket/index.html").then().statusCode(anyOf(is(200), is(204)));
        given().put("/apigw-bucket").then().statusCode(anyOf(is(200), is(204)));
        // Store the object at the same key path that CloudFront will forward (/data/data.json)
        given().contentType("text/plain").body("api response")
               .put("/apigw-bucket/data/data.json").then().statusCode(anyOf(is(200), is(204)));

        String cfBody = """
                <DistributionConfig>
                  <CallerReference>multi-origin-test</CallerReference>
                  <Enabled>true</Enabled>
                  <Origins>
                    <Quantity>2</Quantity>
                    <Items>
                      <Origin>
                        <Id>static</Id>
                        <DomainName>api-bucket.s3.amazonaws.com</DomainName>
                      </Origin>
                      <Origin>
                        <Id>api</Id>
                        <DomainName>apigw-bucket.s3.amazonaws.com</DomainName>
                      </Origin>
                    </Items>
                  </Origins>
                  <DefaultCacheBehavior>
                    <TargetOriginId>static</TargetOriginId>
                    <ViewerProtocolPolicy>allow-all</ViewerProtocolPolicy>
                  </DefaultCacheBehavior>
                  <CacheBehaviors>
                    <Quantity>1</Quantity>
                    <Items>
                      <CacheBehavior>
                        <PathPattern>/data/*</PathPattern>
                        <TargetOriginId>api</TargetOriginId>
                        <ViewerProtocolPolicy>allow-all</ViewerProtocolPolicy>
                      </CacheBehavior>
                    </Items>
                  </CacheBehaviors>
                </DistributionConfig>
                """;

        String cfResponse = given()
                .contentType("text/xml")
                .body(cfBody)
            .when()
                .post("/2020-05-31/distribution")
            .then()
                .statusCode(201)
                .extract().body().asString();

        String cfId     = extractTag(cfResponse, "Id");
        String cfDomain = extractTag(cfResponse, "DomainName");

        // Default origin → api-bucket
        given().header("Host", cfDomain).get("/index.html")
               .then().statusCode(200).body(equalTo("static content"));

        // /data/* → apigw-bucket
        given().header("Host", cfDomain).get("/data/data.json")
               .then().statusCode(200).body(equalTo("api response"));

        // Cleanup
        given().delete("/2020-05-31/distribution/" + cfId).then().statusCode(204);
    }

    // ─────────────── Cleanup ───────────────

    @Test
    @Order(40)
    void deleteDistribution() {
        given()
            .when()
                .delete("/2020-05-31/distribution/" + distributionId)
            .then()
                .statusCode(204);

        given()
            .when()
                .get("/2020-05-31/distribution/" + distributionId)
            .then()
                .statusCode(404)
                .body(containsString("NoSuchDistribution"));
    }

    // ─────────────── Helper ───────────────

    private String extractTag(String xml, String tag) {
        int start = xml.indexOf("<" + tag + ">");
        int end = xml.indexOf("</" + tag + ">", start);
        if (start < 0 || end < 0) return "";
        return xml.substring(start + tag.length() + 2, end);
    }
}
