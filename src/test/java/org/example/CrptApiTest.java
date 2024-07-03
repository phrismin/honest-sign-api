package org.example;

import org.junit.Test;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

public class CrptApiTest {

    @Test
    public void createDocument() throws InterruptedException {
        CrptApi crptApi = CrptApi.getInstance(TimeUnit.MINUTES, 5);
        for (int i = 0; i < 5; i++) {
            if (i == 1) Thread.sleep(10000);
            crptApi.createDocument(new CrptApi.Document(), "Signature");
        }
        for (int i = 0; i < 2; i++) {
            crptApi.createDocument(new CrptApi.Document(), "Signature");
        }
    }
}