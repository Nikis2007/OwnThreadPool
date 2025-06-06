package org.example;


import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

public class Main {
    public static void main(String[] args) throws InterruptedException {
        CustomThreadPool pool = new CustomThreadPool(
                2, // corePoolSize
                4, // maxPoolSize
                5, // keepAliveTime (sec)
                TimeUnit.SECONDS,
                5, // queueSize
                1  // minSpareThreads
        );

        // Имитация нагрузки
        for (int i = 0; i < 20; i++) {
            final int taskId = i;
            try {
                pool.execute(() -> {
                    System.out.println("Task " + taskId + " started");
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    System.out.println("Task " + taskId + " completed");
                });
            } catch (RejectedExecutionException e) {
                System.out.println("Task " + taskId + " rejected");
            }
            Thread.sleep(200);
        }

        // Завершение работы
        Thread.sleep(3000);
        pool.shutdown();
    }
}