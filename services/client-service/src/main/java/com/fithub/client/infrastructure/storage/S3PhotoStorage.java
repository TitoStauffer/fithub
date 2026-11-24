package com.fithub.client.infrastructure.storage;

import com.fithub.client.domain.assessment.PhotoStorage;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

/**
 * Fotos no SeaweedFS pela API S3 (DEV-0048, ADR-006). O bucket é privado (nunca recebe política pública); leitura e
 * escrita só por URL pré-assinada de {@link PhotoStorage#URL_VALIDITY}. As URLs são assinadas com o endereço público
 * ({@code fithub.storage.public-endpoint}), o que o app alcança.
 */
@Component
class S3PhotoStorage implements PhotoStorage {

    private final S3Client client;
    private final S3Presigner presigner;
    private final String bucket;
    private final AtomicBoolean bucketReady = new AtomicBoolean();

    S3PhotoStorage(
            @Value("${fithub.storage.endpoint}") String endpoint,
            @Value("${fithub.storage.public-endpoint}") String publicEndpoint,
            @Value("${fithub.storage.region}") String region,
            @Value("${fithub.storage.access-key}") String accessKey,
            @Value("${fithub.storage.secret-key}") String secretKey,
            @Value("${fithub.storage.bucket}") String bucket) {
        var credentials = StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey));
        // SeaweedFS (e o MinIO) pedem o bucket no caminho, não no subdomínio.
        var pathStyle = S3Configuration.builder().pathStyleAccessEnabled(true).build();
        this.client = S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.of(region))
                .credentialsProvider(credentials)
                .serviceConfiguration(pathStyle)
                .httpClient(UrlConnectionHttpClient.create())
                .build();
        this.presigner = S3Presigner.builder()
                .endpointOverride(URI.create(publicEndpoint))
                .region(Region.of(region))
                .credentialsProvider(credentials)
                .serviceConfiguration(pathStyle)
                .build();
        this.bucket = bucket;
    }

    /**
     * Cria o bucket na primeira vez que uma foto vai ser enviada (ambiente local e testes); em produção ele já existe,
     * privado. Não roda na subida: o serviço sobe mesmo sem o armazenamento no ar.
     */
    private void ensureBucket() {
        if (bucketReady.get()) {
            return;
        }
        try {
            client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
        } catch (NoSuchBucketException missing) {
            client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
        } catch (S3Exception other) {
            if (other.statusCode() != 404) {
                throw other;
            }
            client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
        }
        bucketReady.set(true);
    }

    @Override
    public URI presignUpload(String objectKey, String contentType) {
        ensureBucket();
        var request = PutObjectPresignRequest.builder()
                .signatureDuration(URL_VALIDITY)
                .putObjectRequest(PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(objectKey)
                        .contentType(contentType)
                        .build())
                .build();
        return toUri(presigner.presignPutObject(request).url().toString());
    }

    @Override
    public URI presignDownload(String objectKey) {
        var request = GetObjectPresignRequest.builder()
                .signatureDuration(URL_VALIDITY)
                .getObjectRequest(
                        GetObjectRequest.builder().bucket(bucket).key(objectKey).build())
                .build();
        return toUri(presigner.presignGetObject(request).url().toString());
    }

    @Override
    public Optional<StoredObject> describe(String objectKey) {
        try {
            HeadObjectResponse head = client.headObject(
                    HeadObjectRequest.builder().bucket(bucket).key(objectKey).build());
            return Optional.of(new StoredObject(head.contentType(), head.contentLength()));
        } catch (NoSuchKeyException missing) {
            return Optional.empty();
        } catch (S3Exception other) {
            if (other.statusCode() == 404) {
                return Optional.empty();
            }
            throw other;
        }
    }

    @Override
    public void delete(String objectKey) {
        client.deleteObject(
                DeleteObjectRequest.builder().bucket(bucket).key(objectKey).build());
    }

    @Override
    public void deleteAll(String prefix) {
        list(prefix).forEach(stored -> delete(stored.objectKey()));
    }

    @Override
    public List<StoredKey> list(String prefix) {
        try {
            return client
                    .listObjectsV2Paginator(ListObjectsV2Request.builder()
                            .bucket(bucket)
                            .prefix(prefix)
                            .build())
                    .contents()
                    .stream()
                    .map(object -> new StoredKey(object.key(), object.lastModified()))
                    .toList();
        } catch (NoSuchBucketException missing) {
            return List.of();
        } catch (S3Exception other) {
            if (other.statusCode() == 404) {
                return List.of();
            }
            throw other;
        }
    }

    private static URI toUri(String url) {
        return URI.create(url);
    }
}
