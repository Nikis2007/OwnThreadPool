package org.example;

import java.util.concurrent.Callable;
import java.util.concurrent.Future;

public interface CustomExecutor {
    void execute(Runnable command);
    <T> Future<T> submit(Callable<T> task);
    void shutdown();
    void shutdownNow();

}
