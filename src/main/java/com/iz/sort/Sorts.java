package com.iz.sort;

import java.util.Arrays;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;

public final class Sorts {

    private Sorts() {}

    // ---------- BUBBLE SORT ----------
    public static void bubbleSerial(int[] a) {
        int n = a.length;
        for (int i = 0; i < n - 1; i++) {
            boolean swapped = false;
            for (int j = 0; j < n - 1 - i; j++) {
                if (a[j] > a[j + 1]) {
                    int t = a[j]; a[j] = a[j + 1]; a[j + 1] = t;
                    swapped = true;
                }
            }
            if (!swapped) break;
        }
    }

    // Bubble paralelo: estratégia odd-even transposition sort
    public static void bubbleParallel(int[] a, int threads) {
        int n = a.length;
        ForkJoinPool pool = new ForkJoinPool(threads);
        try {
            for (int phase = 0; phase < n; phase++) {
                final int start = (phase % 2 == 0) ? 0 : 1;
                pool.submit(() -> {
                    java.util.stream.IntStream.range(0, (n - start) / 2)
                        .parallel()
                        .forEach(k -> {
                            int j = start + 2 * k;
                            if (j + 1 < n && a[j] > a[j + 1]) {
                                int t = a[j]; a[j] = a[j + 1]; a[j + 1] = t;
                            }
                        });
                }).join();
            }
        } finally {
            pool.shutdown();
        }
    }

    // ---------- INSERTION SORT ----------
    public static void insertionSerial(int[] a) {
        for (int i = 1; i < a.length; i++) {
            int key = a[i], j = i - 1;
            while (j >= 0 && a[j] > key) { a[j + 1] = a[j]; j--; }
            a[j + 1] = key;
        }
    }

    // Insertion paralelo: ordena blocos em paralelo, depois merge pairwise
    public static void insertionParallel(int[] a, int threads) {
        int n = a.length;
        int blockSize = Math.max(1, (int) Math.ceil((double) n / threads));
        ForkJoinPool pool = new ForkJoinPool(threads);
        try {
            pool.submit(() ->
                java.util.stream.IntStream.range(0, threads).parallel().forEach(t -> {
                    int from = t * blockSize;
                    int to = Math.min(n, from + blockSize);
                    if (from < to) insertionRange(a, from, to);
                })
            ).join();
        } finally {
            pool.shutdown();
        }
        // merge sequencial dos blocos ordenados
        int[] buf = new int[n];
        for (int size = blockSize; size < n; size *= 2) {
            for (int left = 0; left < n; left += 2 * size) {
                int mid = Math.min(left + size, n);
                int right = Math.min(left + 2 * size, n);
                merge(a, buf, left, mid, right);
            }
        }
    }

    private static void insertionRange(int[] a, int from, int to) {
        for (int i = from + 1; i < to; i++) {
            int key = a[i], j = i - 1;
            while (j >= from && a[j] > key) { a[j + 1] = a[j]; j--; }
            a[j + 1] = key;
        }
    }

    // ---------- MERGE SORT ----------
    public static void mergeSerial(int[] a) {
        if (a.length < 2) return;
        int[] buf = new int[a.length];
        mergeSortRec(a, buf, 0, a.length);
    }

    private static void mergeSortRec(int[] a, int[] buf, int lo, int hi) {
        if (hi - lo < 2) return;
        int mid = (lo + hi) >>> 1;
        mergeSortRec(a, buf, lo, mid);
        mergeSortRec(a, buf, mid, hi);
        merge(a, buf, lo, mid, hi);
    }

    private static void merge(int[] a, int[] buf, int lo, int mid, int hi) {
        System.arraycopy(a, lo, buf, lo, hi - lo);
        int i = lo, j = mid, k = lo;
        while (i < mid && j < hi) a[k++] = (buf[i] <= buf[j]) ? buf[i++] : buf[j++];
        while (i < mid) a[k++] = buf[i++];
        while (j < hi) a[k++] = buf[j++];
    }

    public static void mergeParallel(int[] a, int threads) {
        if (a.length < 2) return;
        int[] buf = new int[a.length];
        ForkJoinPool pool = new ForkJoinPool(threads);
        try {
            pool.invoke(new MergeTask(a, buf, 0, a.length, threshold(a.length, threads)));
        } finally {
            pool.shutdown();
        }
    }

    private static int threshold(int n, int threads) {
        return Math.max(1024, n / (threads * 4));
    }

    private static final class MergeTask extends RecursiveAction {
        private final int[] a, buf;
        private final int lo, hi, th;
        MergeTask(int[] a, int[] buf, int lo, int hi, int th) {
            this.a = a; this.buf = buf; this.lo = lo; this.hi = hi; this.th = th;
        }
        @Override protected void compute() {
            if (hi - lo <= th) { mergeSortRec(a, buf, lo, hi); return; }
            int mid = (lo + hi) >>> 1;
            invokeAll(new MergeTask(a, buf, lo, mid, th), new MergeTask(a, buf, mid, hi, th));
            merge(a, buf, lo, mid, hi);
        }
    }

    // ---------- QUICK SORT ----------
    public static void quickSerial(int[] a) { quickRec(a, 0, a.length - 1); }

    private static void quickRec(int[] a, int lo, int hi) {
        while (lo < hi) {
            int p = partition(a, lo, hi);
            if (p - lo < hi - p - 1) { quickRec(a, lo, p); lo = p + 1; }
            else { quickRec(a, p + 1, hi); hi = p; }
        }
    }

    private static int partition(int[] a, int lo, int hi) {
        int mid = lo + ((hi - lo) >>> 1);
        int pivot = median(a[lo], a[mid], a[hi]);
        int i = lo - 1, j = hi + 1;
        while (true) {
            do i++; while (a[i] < pivot);
            do j--; while (a[j] > pivot);
            if (i >= j) return j;
            int t = a[i]; a[i] = a[j]; a[j] = t;
        }
    }

    private static int median(int x, int y, int z) {
        if (x > y) { int t = x; x = y; y = t; }
        if (y > z) { int t = y; y = z; z = t; }
        if (x > y) { int t = x; x = y; y = t; }
        return y;
    }

    public static void quickParallel(int[] a, int threads) {
        ForkJoinPool pool = new ForkJoinPool(threads);
        try {
            pool.invoke(new QuickTask(a, 0, a.length - 1, threshold(a.length, threads)));
        } finally {
            pool.shutdown();
        }
    }

    private static final class QuickTask extends RecursiveAction {
        private final int[] a;
        private final int lo, hi, th;
        QuickTask(int[] a, int lo, int hi, int th) {
            this.a = a; this.lo = lo; this.hi = hi; this.th = th;
        }
        @Override protected void compute() {
            if (hi - lo <= th) { quickRec(a, lo, hi); return; }
            if (lo >= hi) return;
            int p = partition(a, lo, hi);
            invokeAll(new QuickTask(a, lo, p, th), new QuickTask(a, p + 1, hi, th));
        }
    }

    // ---------- Util ----------
    public static boolean isSorted(int[] a) {
        for (int i = 1; i < a.length; i++) if (a[i - 1] > a[i]) return false;
        return true;
    }

    public static int[] copy(int[] a) { return Arrays.copyOf(a, a.length); }
}
