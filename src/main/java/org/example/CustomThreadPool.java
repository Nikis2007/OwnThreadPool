package org.example;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;

public class CustomThreadPool implements CustomExecutor {
    // Конфигурационные параметры
    private final int corePoolSize;
    private final int maxPoolSize;
    private final long keepAliveTime;
    private final TimeUnit timeUnit;
    private final int queueSize;
    private final int minSpareThreads;

    // Механизм распределения задач
    private final List<BlockingQueue<Runnable>> queues;
    private final AtomicInteger nextQueueIndex = new AtomicInteger(0);

    // Управление потоками
    private final List<Worker> workers = Collections.synchronizedList(new ArrayList<>());
    private final ThreadFactory threadFactory;
    private final RejectedExecutionHandler rejectionHandler;
    private volatile boolean isShutdown = false;

    // Конструктор
    public CustomThreadPool(int corePoolSize, int maxPoolSize,
                            long keepAliveTime, TimeUnit timeUnit,
                            int queueSize, int minSpareThreads) {
        this.corePoolSize = corePoolSize;
        this.maxPoolSize = maxPoolSize;
        this.keepAliveTime = keepAliveTime;
        this.timeUnit = timeUnit;
        this.queueSize = queueSize;
        this.minSpareThreads = minSpareThreads;

        this.queues = new ArrayList<>(corePoolSize);
        for (int i = 0; i < corePoolSize; i++) {
            queues.add(new LinkedBlockingQueue<>(queueSize));
        }

        this.threadFactory = new LoggingThreadFactory();
        this.rejectionHandler = new CustomRejectionHandler();

        // Запуск основных рабочих потоков
        for (int i = 0; i < corePoolSize; i++) {
            createWorker(i);
        }
    }

    @Override
    public void execute(Runnable command) {
        if (isShutdown) {
            handleRejection(command);
            return;
        }

        // Round Robin распределение
        int index = nextQueueIndex.getAndUpdate(i -> (i + 1) % queues.size());
        BlockingQueue<Runnable> queue = queues.get(index);

        if (!queue.offer(command)) {
            if (workers.size() < maxPoolSize) {
                createWorker(index);
                if (!queue.offer(command)) {
                    handleRejection(command);
                }
            } else {
                handleRejection(command);
            }
        } else {
            checkMinSpareThreads();
            log("[Pool] Task accepted into queue #" + index);
        }
    }

    private void handleRejection(Runnable command) {
        log("[Rejected] Task was rejected due to overload!");
        throw new RejectedExecutionException("Task " + command + " rejected");
    }

    @Override
    public <T> Future<T> submit(Callable<T> task) {
        FutureTask<T> future = new FutureTask<>(task);
        execute(future);
        return future;
    }

    @Override
    public void shutdown() {
        isShutdown = true;
        workers.forEach(Worker::interruptIfIdle);
    }

    @Override
    public void shutdownNow() {
        isShutdown = true;
        queues.forEach(Queue::clear);
        workers.forEach(Worker::interruptNow);
    }

    // Внутренние классы и методы
    private class Worker extends Thread {
        private final int queueIndex;
        private volatile boolean running = true;

        Worker(int queueIndex) {
            this.queueIndex = queueIndex;
        }

        @Override
        public void run() {
            BlockingQueue<Runnable> queue = queues.get(queueIndex);
            try {
                while (running && !isShutdown) {
                    Runnable task = queue.poll(keepAliveTime, timeUnit);
                    if (task != null) {
                        log("[Worker] " + getName() + " executes task");
                        task.run();
                    } else if (workers.size() > corePoolSize) {
                        log("[Worker] " + getName() + " idle timeout, stopping");
                        running = false;
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                workers.remove(this);
                log("[Worker] " + getName() + " terminated");
            }
        }

        void interruptIfIdle() {
            if (!isAlive()) return;
            interrupt();
        }

        void interruptNow() {
            running = false;
            interrupt();
        }
    }

    private class LoggingThreadFactory implements ThreadFactory {
        private final AtomicInteger counter = new AtomicInteger(1);

        @Override
        public Thread newThread(Runnable r) {
            Thread thread = new Thread(r, "MyPool-worker-" + counter.getAndIncrement());
            log("[ThreadFactory] Creating new thread: " + thread.getName());
            return thread;
        }
    }

    private class CustomRejectionHandler implements RejectedExecutionHandler {
        @Override
        public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
            log("[Rejected] Task was rejected due to overload!");
            throw new RejectedExecutionException("Task " + r + " rejected");
        }
    }

    private void createWorker(int queueIndex) {
        synchronized (workers) {
            if (workers.size() >= maxPoolSize || isShutdown) return;

            Worker worker = new Worker(queueIndex);
            Thread thread = threadFactory.newThread(worker);
            worker.setName(thread.getName());
            workers.add(worker);
            thread.start();
        }
    }

    private void checkMinSpareThreads() {
        synchronized (workers) {
            long idleCount = workers.stream().filter(w -> !w.isAlive()).count();
            if (idleCount < minSpareThreads && workers.size() < maxPoolSize) {
                createWorker(nextQueueIndex.getAndIncrement() % queues.size());
            }
        }
    }

    private void log(String message) {
        System.out.println(Thread.currentThread().getName() + " " + message);
    }
}