package uz.tgforward.service;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import org.springframework.stereotype.Service;
import java.net.URI;
import java.nio.file.Path;

@Service
public class R2Service {
    private final S3Client s3Client;
    private final String bucketName = System.getenv("forwarderbot-store");

    public R2Service() {
        this.s3Client = S3Client.builder()
                .endpointOverride(URI.create("https://b0edd42c4521bc785a9d4855984c880a.r2.cloudflarestorage.com"))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(
                                "5c50091804a46a4110149cbc17cde993",
                                "c4f1b4a9480c614a82896b2abcdb123bd3157b246d5f0ad8eb602c30116f0dfb"
                        )))
                .region(Region.US_EAST_1) // R2 uchun shunday qoladi
                .build();
    }

    public void upload(String key, Path path) {
        s3Client.putObject(PutObjectRequest.builder().bucket(bucketName).key(key).build(), path);
    }

    public void download(String key, Path path) {
        try {
            s3Client.getObject(GetObjectRequest.builder().bucket(bucketName).key(key).build(), path);
        } catch (Exception e) {
            System.out.println("Fayl topilmadi, yangi sessiya yaratiladi.");
        }
    }
}