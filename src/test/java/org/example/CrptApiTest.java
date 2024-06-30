package org.example;

import org.junit.Test;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

public class CrptApiTest {

    @Test
    public void createDocument() throws IOException, InterruptedException {
        for (int i = 0; i < 10; i++) {
            CrptApi crptApi = CrptApi.getInstance(TimeUnit.MINUTES, 2);
            crptApi.createDocument(new CrptApi.Document(), "Signature");
        }
    }
}