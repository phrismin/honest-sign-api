package org.example;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.io.IOException;

public class CrptApi {
    private final TimeUnit timeUnit;
    private final int requestLimit;
    private static final Queue<Long> queue = new ArrayDeque<>();
    private static CrptApi instance;

    private CrptApi(TimeUnit timeUnit, int requestLimit) {
        this.timeUnit = timeUnit;
        this.requestLimit = requestLimit;
    }

    public static CrptApi getInstance(TimeUnit timeUnit, int requestLimit) {
        CrptApi localInstance = instance;
        if (localInstance == null) {
            synchronized (CrptApi.class) {
                localInstance = instance;
                if (localInstance == null) {
                    localInstance = new CrptApi(timeUnit, requestLimit);
                    instance = localInstance;
                }
            }
        }
        return localInstance;
    }

    public void createDocument(Document document, String signature) {
        synchronized (this) {
            long currentRequestTime = System.currentTimeMillis();
            if (queue.size() == requestLimit) {
                long firstRequestTime = queue.element();
                long timeBetweenRequests = currentRequestTime - firstRequestTime;
                if (timeBetweenRequests < timeUnit.toMillis(1)) {
                    try {
                        wait(timeUnit.toMillis(1) - timeBetweenRequests);
                    } catch (InterruptedException e) {
                        System.out.println("Thread interrupted");
                    }
                }
                queue.poll();
                queue.add(currentRequestTime);

            } else {
                queue.add(currentRequestTime);
            }

            try {
                String json = new ObjectMapper()
                        .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                        .writeValueAsString(document);
                HttpClient client = HttpClient.newBuilder()
                        .version(HttpClient.Version.HTTP_2)
                        .build();
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:8000/"))
                        .header("Content-Type", "application/json")
                        .header("Signature", signature)
                        .POST(HttpRequest.BodyPublishers.ofString(json))
                        .build();
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                System.out.println(response.statusCode() + ":" + response.body());
            } catch (JsonProcessingException e) {
                System.out.println("Error converting document to json");
            } catch (IOException | InterruptedException e) {
                System.out.println("Server is not available");
            }
        }
    }

    public record Document(Description description, String docId, String docStatus, String docType,
                           boolean importRequest, String ownerInn, String participantInn, String producerInn,
                           String productionDate, String productionType, List<Product> products, String regDate,
                           String regNumber) {
        public Document() {
            this(null, null, null, null,
                    false, null, null, null,
                    null, null, null, null,
                    null);
        }
    }

    public record Description(String participantInn) {
        public Description() {
            this(null);
        }
    }

    public record Product(String certificateDocument, String certificateDocumentDate,
                          String certificateDocumentNumber,
                          String ownerInn, String producerInn, String productionDate, String tnvedCode,
                          String uitCode,
                          String uituCode) {
        public Product() {
            this(null, null, null,
                    null, null, null, null, null,
                    null);
        }
    }
}