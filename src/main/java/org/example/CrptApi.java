package org.example;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class CrptApi {
    private final TimeUnit timeUnit;
    private final int requestLimit;
    private final AtomicInteger requestCounter = new AtomicInteger(0);
    private volatile long lastResetTime = new Date().getTime();
    private static CrptApi instance;
    private Lock lock;
    private static ExecutorService executors = Executors.newFixedThreadPool(14);

    private CrptApi(TimeUnit timeUnit, int requestLimit) {
        this.timeUnit = timeUnit;
        this.requestLimit = requestLimit;
        this.lock = new ReentrantLock();
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

    public String createDocument(Document document, String signature) throws IOException, InterruptedException {
        // TODO semaphore не нужны потому что неограниченное количество потоков от
        //  обработки запросов, fixedPool
        Future<?> future = executors.submit(() -> {
            System.out.println(this);
            System.out.println(Thread.currentThread().getName() + " захватил synchronized блок");
            long currentTime = System.currentTimeMillis();
            long timePassed = currentTime - lastResetTime;
            // если времени прошло больше, чем ограниченный интервал, то количество запросов сбрасывается
            if (timePassed >= timeUnit.toMillis(1)) {
                requestCounter.set(0);
                lastResetTime = new Date(currentTime).getTime();
            }

            while (requestCounter.get() >= requestLimit) {
                System.out.println(Thread.currentThread().getName() + " останавливается на " + (timeUnit.toMillis(1) - timePassed) + " мс");
                try {
                    wait(timeUnit.toMillis(1) - timePassed);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }

                currentTime = System.currentTimeMillis();
                timePassed = currentTime - lastResetTime;
                // если времени прошло больше, чем ограниченный интервал, то количество запросов сбрасывается
                if (timePassed >= timeUnit.toMillis(1)) {
                    requestCounter.set(0);
                    lastResetTime = new Date(currentTime).getTime();
                }
            }

            String json = new ObjectMapper().setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE).writeValueAsString(document);
            HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_2).build();
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create("http://localhost:8000/")).header("Content-Type", "application/json").header("Signature", signature).POST(HttpRequest.BodyPublishers.ofString(json)).build();
            HttpResponse<String> response;
            try {
                response = client.send(request, HttpResponse.BodyHandlers.ofString());
            } catch (IOException | InterruptedException e) {
                throw new RuntimeException(e);
            }
            System.out.println(response.statusCode() + " " + response.body());

            requestCounter.incrementAndGet();
            System.out.println(Thread.currentThread().getName() + " выходит из  synchronized");
            return response.body();
        });
        executors.shutdown();
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

    public record Product(String certificateDocument, String certificateDocumentDate, String certificateDocumentNumber,
                          String ownerInn, String producerInn, String productionDate, String tnvedCode, String uitCode,
                          String uituCode) {
        public Product() {
            this(null, null, null,
                    null, null, null, null, null,
                    null);
        }
    }
}

