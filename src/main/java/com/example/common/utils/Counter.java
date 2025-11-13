package com.example.common.utils;

import java.util.concurrent.atomic.AtomicInteger;

public class Counter {

    private final AtomicInteger operationCount = new AtomicInteger(0);
    private final AtomicInteger failuresCount = new AtomicInteger(0);
    private final AtomicInteger successesCount = new AtomicInteger(0);

    public void incrementOperationCount() {
        operationCount.incrementAndGet();
    }

    public void incrementFailuresCount() {
        failuresCount.incrementAndGet();
    }

    public void incrementSuccessesCount() {
        successesCount.incrementAndGet();
    }

    public int getOperationCount() {
        return operationCount.get();
    }

    public int getFailuresCount() {
        return failuresCount.get();
    }

    public int getSuccessesCount() {
        return successesCount.get();
    }
}
