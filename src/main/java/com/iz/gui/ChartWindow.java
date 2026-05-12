package com.iz.gui;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.List;

/** Gráfico dinâmico de linha: eixo X = tamanho, Y = tempo médio (ms). Renderizado em Swing puro. */
public class ChartWindow extends JFrame {

    private final List<Row> rows;
    private final JComboBox<String> algoBox;
    private final JComboBox<String> modeBox;
    private final ChartPanel chart;

    record Row(String algo, String mode, int size, int threads, double ms) {}

    public ChartWindow(Path csv) throws IOException {
        super("Gráfico de Desempenho — " + csv.getFileName());
        rows = read(csv);
        TreeSet<String> algos = new TreeSet<>();
        TreeSet<String> modes = new TreeSet<>();
        for (Row r : rows) { algos.add(r.algo); modes.add(r.mode); }
        algoBox = new JComboBox<>(algos.toArray(new String[0]));
        modeBox = new JComboBox<>(modes.toArray(new String[0]));
        chart = new ChartPanel();

        JPanel top = new JPanel();
        top.add(new JLabel("Algoritmo:")); top.add(algoBox);
        top.add(new JLabel("Distribuição:")); top.add(modeBox);
        algoBox.addActionListener(e -> chart.repaint());
        modeBox.addActionListener(e -> chart.repaint());

        setLayout(new BorderLayout());
        add(top, BorderLayout.NORTH);
        add(chart, BorderLayout.CENTER);
        setSize(900, 600);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
    }

    private static List<Row> read(Path csv) throws IOException {
        List<Row> rs = new ArrayList<>();
        try (BufferedReader r = Files.newBufferedReader(csv)) {
            String line = r.readLine(); // header
            while ((line = r.readLine()) != null) {
                String[] p = line.split(",");
                rs.add(new Row(p[0], p[1], Integer.parseInt(p[2]),
                        Integer.parseInt(p[3]), Double.parseDouble(p[6])));
            }
        }
        return rs;
    }

    private class ChartPanel extends JPanel {
        @Override protected void paintComponent(Graphics g0) {
            super.paintComponent(g0);
            Graphics2D g = (Graphics2D) g0;
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int W = getWidth(), H = getHeight();
            g.setColor(Color.WHITE); g.fillRect(0, 0, W, H);

            String algo = (String) algoBox.getSelectedItem();
            String mode = (String) modeBox.getSelectedItem();
            if (algo == null || mode == null) return;

            // agrupar por threads -> (size -> tempo médio)
            Map<Integer, TreeMap<Integer, double[]>> byThread = new TreeMap<>();
            for (Row r : rows) {
                if (!r.algo.equals(algo) || !r.mode.equals(mode)) continue;
                byThread.computeIfAbsent(r.threads, k -> new TreeMap<>())
                        .computeIfAbsent(r.size, k -> new double[]{0, 0});
                double[] acc = byThread.get(r.threads).get(r.size);
                acc[0] += r.ms; acc[1] += 1;
            }
            if (byThread.isEmpty()) {
                g.setColor(Color.BLACK);
                g.drawString("Sem dados para a seleção.", 20, 20);
                return;
            }

            // escala
            double maxY = 0, maxX = 0, minX = Double.MAX_VALUE;
            for (var m : byThread.values()) for (var e : m.entrySet()) {
                double avg = e.getValue()[0] / e.getValue()[1];
                if (avg > maxY) maxY = avg;
                if (e.getKey() > maxX) maxX = e.getKey();
                if (e.getKey() < minX) minX = e.getKey();
            }
            if (maxY <= 0) maxY = 1;
            int pad = 60;
            int plotW = W - 2 * pad, plotH = H - 2 * pad;

            // eixos
            g.setColor(Color.BLACK);
            g.drawLine(pad, H - pad, W - pad, H - pad);
            g.drawLine(pad, H - pad, pad, pad);
            g.drawString("Tamanho (n)", W / 2 - 30, H - 15);
            g.drawString("Tempo (ms)", 10, pad - 10);
            g.drawString(String.format("%s — %s", algo, mode), W / 2 - 60, 25);

            // grid + labels Y
            for (int i = 0; i <= 5; i++) {
                int y = H - pad - (int) (i / 5.0 * plotH);
                g.setColor(new Color(230, 230, 230));
                g.drawLine(pad, y, W - pad, y);
                g.setColor(Color.BLACK);
                g.drawString(String.format("%.1f", i / 5.0 * maxY), 5, y + 4);
            }

            // cores por nº de threads
            Color[] palette = {
                new Color(31, 119, 180), new Color(255, 127, 14),
                new Color(44, 160, 44), new Color(214, 39, 40),
                new Color(148, 103, 189), new Color(140, 86, 75)
            };
            int legendY = pad + 10;
            int ci = 0;
            double xRange = Math.max(1, maxX - minX);
            for (var entry : byThread.entrySet()) {
                int threads = entry.getKey();
                Color col = palette[ci % palette.length];
                g.setColor(col);
                g.setStroke(new BasicStroke(2f));
                Path2D path = new Path2D.Double();
                boolean first = true;
                for (var e : entry.getValue().entrySet()) {
                    int n = e.getKey();
                    double avg = e.getValue()[0] / e.getValue()[1];
                    double x = pad + (n - minX) / xRange * plotW;
                    double y = H - pad - (avg / maxY) * plotH;
                    if (first) { path.moveTo(x, y); first = false; } else path.lineTo(x, y);
                    g.fill(new Ellipse2D.Double(x - 3, y - 3, 6, 6));
                }
                g.draw(path);
                String label = threads == 1 ? "Serial" : (threads + " threads");
                g.drawString(label, W - pad - 100, legendY);
                g.fillRect(W - pad - 115, legendY - 10, 10, 10);
                legendY += 16;
                ci++;
            }

            // labels X
            g.setColor(Color.BLACK);
            int first = (int) minX, last = (int) maxX;
            int xFirst = pad;
            int xLast = pad + plotW;
            g.drawString(String.valueOf(first), xFirst - 10, H - pad + 18);
            g.drawString(String.valueOf(last), xLast - 30, H - pad + 18);
        }
    }
}
