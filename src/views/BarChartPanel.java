package views;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.List;

/** Gráfico de barras desenhado sobre dados consultados em memória (persistência local). */
public class BarChartPanel extends JPanel {
    public record Series(String label, double value) {}

    private List<Series> series = List.of();
    private final Color barColor;
    private String emptyMessage = "Sem movimentos nos últimos 14 dias";

    public BarChartPanel(Color color) {
        this.barColor = color;
        setBackground(Color.WHITE);
        setBorder(new EmptyBorder(10, 10, 10, 10));
        setMinimumSize(new Dimension(320, 200));
        setPreferredSize(new Dimension(360, 220));
    }

    /** Texto mostrado quando a série está vazia ou não tem valores. */
    public void setEmptyMessage(String m) { emptyMessage = m; repaint(); }

    public void setSeries(List<Series> s) {
        this.series = s == null ? List.of() : s;
        repaint();
    }

    @Override protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int w = getWidth(), h = getHeight();
        int left = 44, bottom = 32, top = 16, right = 12;
        int plotW = Math.max(10, w - left - right), plotH = Math.max(10, h - top - bottom);

        if (series.isEmpty() || series.stream().allMatch(s -> s.value() <= 0)) {
            // eixos vazios + mensagem clara em vez de área em branco
            g2.setColor(new Color(225, 231, 238));
            g2.drawLine(left, top, left, top + plotH);
            g2.drawLine(left, top + plotH, left + plotW, top + plotH);
            g2.setColor(Theme.MUTED);
            g2.setFont(new Font("SansSerif", 0, 12));
            g2.drawString(emptyMessage, left + 12, top + plotH / 2 + 4);
            return;
        }

        double max = series.stream().mapToDouble(Series::value).max().orElse(1);
        if (max <= 0) max = 1;

        // grelha horizontal: número de linhas adaptado à altura disponível
        int lines = plotH >= 150 ? 4 : (plotH >= 110 ? 2 : 1);
        g2.setColor(new Color(225, 231, 238));
        for (int i = 0; i <= lines; i++) {
            int y = top + plotH * i / lines;
            g2.drawLine(left, y, left + plotW, y);
        }
        // etiquetas do eixo Y (só quando há espaço para ler)
        g2.setColor(Theme.MUTED);
        g2.setFont(new Font("SansSerif", 0, 10));
        if (plotH >= 110) {
            for (int i = 0; i <= lines; i++) {
                double v = max * (lines - i) / (double) lines;
                String txt = shortNum(v);
                g2.drawString(txt, Math.max(2, left - 10 - g2.getFontMetrics().stringWidth(txt)),
                        top + plotH * i / lines + 4);
            }
        }

        // barras
        int n = series.size();
        int gap = Math.max(4, (plotW / n) / 4);
        int bw = Math.max(8, (plotW - gap * (n + 1)) / n);
        int i = 0;
        g2.setFont(new Font("SansSerif", 0, 10));
        for (Series s : series) {
            int x = left + gap + i * (bw + gap);
            int bh = (int) Math.round((s.value() / max) * (plotH - 6));
            if (bh < 2) bh = 2;                     // barras mínimas visíveis
            int y = top + plotH - bh;
            g2.setColor(barColor);
            g2.fillRoundRect(x, y, bw, bh, 8, 8);
            g2.setColor(barColor.darker());
            g2.drawRoundRect(x, y, bw, bh, 8, 8);
            // etiqueta no topo das barras altas
            if (bh >= 34) {
                g2.setColor(Theme.NAVY);
                String vTxt = shortNum(s.value());
                int vw = g2.getFontMetrics().stringWidth(vTxt);
                g2.drawString(vTxt, x + bw / 2 - vw / 2, y - 3);
            }
            // etiqueta de data por baixo (só se couber)
            g2.setColor(Theme.MUTED);
            String lbl = s.label();
            int fw = g2.getFontMetrics().stringWidth(lbl);
            if (bw >= fw - 2) g2.drawString(lbl, x + bw / 2 - fw / 2, h - 10);
            i++;
        }
    }

    private String shortNum(double v) {
        if (v >= 1_000_000) return String.format("%.1fM", v / 1_000_000);
        if (v >= 10_000) return String.format("%.1fk", v / 1_000);
        return String.format("%.0f", v);
    }
}
