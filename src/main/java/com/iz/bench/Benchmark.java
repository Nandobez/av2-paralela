package com.iz.bench;

import com.iz.sort.Sorts;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import java.util.function.Consumer;

public final class Benchmark {

    public enum Algo { BUBBLE, INSERTION, MERGE, QUICK }
    public enum Mode { ASC, DESC, RANDOM, NEARLY_SORTED }

    public static int[] generate(int n, Mode mode, long seed) {
        int[] a = new int[n];
        Random r = new Random(seed);
        switch (mode) {
            case ASC -> { for (int i = 0; i < n; i++) a[i] = i; }
            case DESC -> { for (int i = 0; i < n; i++) a[i] = n - i; }
            case RANDOM -> { for (int i = 0; i < n; i++) a[i] = r.nextInt(); }
            case NEARLY_SORTED -> {
                for (int i = 0; i < n; i++) a[i] = i;
                int swaps = Math.max(1, n / 100);
                for (int k = 0; k < swaps; k++) {
                    int x = r.nextInt(n), y = r.nextInt(n);
                    int t = a[x]; a[x] = a[y]; a[y] = t;
                }
            }
        }
        return a;
    }

    public static long runOnce(Algo algo, int[] data, int threads) {
        int[] a = Sorts.copy(data);
        long t0 = System.nanoTime();
        switch (algo) {
            case BUBBLE -> { if (threads <= 1) Sorts.bubbleSerial(a); else Sorts.bubbleParallel(a, threads); }
            case INSERTION -> { if (threads <= 1) Sorts.insertionSerial(a); else Sorts.insertionParallel(a, threads); }
            case MERGE -> { if (threads <= 1) Sorts.mergeSerial(a); else Sorts.mergeParallel(a, threads); }
            case QUICK -> { if (threads <= 1) Sorts.quickSerial(a); else Sorts.quickParallel(a, threads); }
        }
        long elapsed = System.nanoTime() - t0;
        if (!Sorts.isSorted(a)) throw new IllegalStateException("Falha: " + algo + " t=" + threads);
        return elapsed;
    }

    public record Config(int[] sizes, int[] threadCounts, Mode[] modes, Algo[] algos, int samples) {}

    public static void runSuite(Config cfg, Path csvOut, Consumer<String> progress) throws IOException {
        try (BufferedWriter w = Files.newBufferedWriter(csvOut)) {
            w.write("algoritmo,modo,tamanho,threads,amostra,tempo_ns,tempo_ms\n");
            long total = (long) cfg.algos.length * cfg.modes.length * cfg.sizes.length
                       * cfg.threadCounts.length * cfg.samples;
            long done = 0;
            for (Algo algo : cfg.algos) {
                for (Mode mode : cfg.modes) {
                    for (int n : cfg.sizes) {
                        if ((algo == Algo.BUBBLE || algo == Algo.INSERTION) && n > 50_000) {
                            done += (long) cfg.threadCounts.length * cfg.samples;
                            progress.accept("Pulando " + algo + " n=" + n + " (muito grande)");
                            continue;
                        }
                        int[] data = generate(n, mode, 42L);
                        for (int t : cfg.threadCounts) {
                            for (int s = 1; s <= cfg.samples; s++) {
                                long ns = runOnce(algo, data, t);
                                w.write(String.format("%s,%s,%d,%d,%d,%d,%.3f%n",
                                        algo, mode, n, t, s, ns, ns / 1e6));
                                done++;
                                progress.accept(String.format("[%d/%d] %s %s n=%d th=%d #%d %.2f ms",
                                        done, total, algo, mode, n, t, s, ns / 1e6));
                            }
                        }
                    }
                }
            }
            w.flush();
        }
    }
}
