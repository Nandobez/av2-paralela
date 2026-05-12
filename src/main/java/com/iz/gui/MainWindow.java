package com.iz.gui;

import com.iz.bench.Benchmark;
import com.iz.bench.Benchmark.Algo;
import com.iz.bench.Benchmark.Mode;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.stream.*;

public class MainWindow extends JFrame {

    private final JTextField sizesField = new JTextField("1000,5000,10000,50000,100000");
    private final JTextField threadsField = new JTextField("1,2,4,8");
    private final JTextField samplesField = new JTextField("5");
    private final JCheckBox cbBubble = new JCheckBox("Bubble", true);
    private final JCheckBox cbInsertion = new JCheckBox("Insertion", true);
    private final JCheckBox cbMerge = new JCheckBox("Merge", true);
    private final JCheckBox cbQuick = new JCheckBox("Quick", true);
    private final JCheckBox cbAsc = new JCheckBox("ASC", false);
    private final JCheckBox cbDesc = new JCheckBox("DESC", false);
    private final JCheckBox cbRand = new JCheckBox("RANDOM", true);
    private final JCheckBox cbNear = new JCheckBox("NEARLY_SORTED", false);
    private final JTextArea log = new JTextArea(14, 60);
    private final JButton runBtn = new JButton("Executar benchmark");
    private final JButton chartBtn = new JButton("Ver gráfico");
    private final JLabel statusBar = new JLabel("Pronto. Núcleos disponíveis: " + Runtime.getRuntime().availableProcessors());
    private Path lastCsv;

    public MainWindow() {
        super("Análise de Algoritmos de Ordenação — Serial vs Paralelo");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout(8, 8));

        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(4, 4, 4, 4);
        g.fill = GridBagConstraints.HORIZONTAL;
        g.gridx = 0; g.gridy = 0; form.add(new JLabel("Tamanhos (vírgula):"), g);
        g.gridx = 1; g.weightx = 1; form.add(sizesField, g);
        g.gridx = 0; g.gridy = 1; g.weightx = 0; form.add(new JLabel("Threads (1=serial):"), g);
        g.gridx = 1; g.weightx = 1; form.add(threadsField, g);
        g.gridx = 0; g.gridy = 2; g.weightx = 0; form.add(new JLabel("Amostras por execução:"), g);
        g.gridx = 1; g.weightx = 1; form.add(samplesField, g);

        JPanel algos = new JPanel();
        algos.setBorder(BorderFactory.createTitledBorder("Algoritmos"));
        algos.add(cbBubble); algos.add(cbInsertion); algos.add(cbMerge); algos.add(cbQuick);

        JPanel modes = new JPanel();
        modes.setBorder(BorderFactory.createTitledBorder("Distribuição dos dados"));
        modes.add(cbAsc); modes.add(cbDesc); modes.add(cbRand); modes.add(cbNear);

        JPanel buttons = new JPanel();
        buttons.add(runBtn);
        buttons.add(chartBtn);
        chartBtn.setEnabled(false);

        log.setEditable(false);
        log.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));

        JPanel top = new JPanel();
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        top.add(form);
        top.add(algos);
        top.add(modes);
        top.add(buttons);

        add(top, BorderLayout.NORTH);
        add(new JScrollPane(log), BorderLayout.CENTER);
        add(statusBar, BorderLayout.SOUTH);

        runBtn.addActionListener(this::onRun);
        chartBtn.addActionListener(e -> openChart());

        pack();
        setLocationRelativeTo(null);
    }

    private void onRun(ActionEvent e) {
        try {
            int[] sizes = parseInts(sizesField.getText());
            int[] threads = parseInts(threadsField.getText());
            int samples = Integer.parseInt(samplesField.getText().trim());
            List<Algo> algoList = new ArrayList<>();
            if (cbBubble.isSelected()) algoList.add(Algo.BUBBLE);
            if (cbInsertion.isSelected()) algoList.add(Algo.INSERTION);
            if (cbMerge.isSelected()) algoList.add(Algo.MERGE);
            if (cbQuick.isSelected()) algoList.add(Algo.QUICK);
            List<Mode> modeList = new ArrayList<>();
            if (cbAsc.isSelected()) modeList.add(Mode.ASC);
            if (cbDesc.isSelected()) modeList.add(Mode.DESC);
            if (cbRand.isSelected()) modeList.add(Mode.RANDOM);
            if (cbNear.isSelected()) modeList.add(Mode.NEARLY_SORTED);
            if (algoList.isEmpty() || modeList.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Selecione ao menos 1 algoritmo e 1 distribuição.");
                return;
            }

            JFileChooser fc = new JFileChooser();
            fc.setSelectedFile(new File("resultados.csv"));
            fc.setFileFilter(new FileNameExtensionFilter("CSV", "csv"));
            if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
            Path out = fc.getSelectedFile().toPath();

            Benchmark.Config cfg = new Benchmark.Config(
                    sizes, threads,
                    modeList.toArray(new Mode[0]),
                    algoList.toArray(new Algo[0]),
                    samples
            );

            runBtn.setEnabled(false);
            log.setText("");
            new SwingWorker<Void, String>() {
                @Override protected Void doInBackground() throws Exception {
                    Benchmark.runSuite(cfg, out, this::publish);
                    return null;
                }
                @Override protected void process(List<String> chunks) {
                    for (String s : chunks) log.append(s + "\n");
                    log.setCaretPosition(log.getDocument().getLength());
                    statusBar.setText(chunks.get(chunks.size() - 1));
                }
                @Override protected void done() {
                    runBtn.setEnabled(true);
                    try { get(); lastCsv = out; chartBtn.setEnabled(true);
                          statusBar.setText("Concluído: " + out);
                          log.append("\nCSV salvo em: " + out + "\n");
                    } catch (Exception ex) {
                        log.append("ERRO: " + ex.getCause() + "\n");
                    }
                }
            }.execute();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Entrada inválida: " + ex.getMessage());
        }
    }

    private void openChart() {
        if (lastCsv == null) return;
        try {
            new ChartWindow(lastCsv).setVisible(true);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Falha ao ler CSV: " + ex.getMessage());
        }
    }

    private static int[] parseInts(String s) {
        return Arrays.stream(s.split(",")).map(String::trim).filter(t -> !t.isEmpty())
                .mapToInt(Integer::parseInt).toArray();
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new MainWindow().setVisible(true));
    }
}
