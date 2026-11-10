package views;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.List;

/** Gráfico de barras simples desenhado sobre dados consultados em memória (persistence local). */
public class BarChartPanel extends JPanel {
    public record Series(String label, double value) {}

    private List<Series> series = List.of();
    private final Color barColor;

    public BarChartPanel(Color color) {
        this.barColor = color;
        setBackground(Color.WHITE);
        setBorder(new EmptyBorder(10, 10, 10, 10));
        setMinimumSize(new Dimension(320, 190));
        setPreferredSize(new Dimension(360, 190));
    }

    public void setSeries(List<Series> s) {
        this.series = s == null ? List.of() : s;
        repaint();
    }

    @Override protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int w = getWidth(), h = getHeight();
        int left = 40, bottom = 36, top = 24, right = 12;
        int plotW = Math.max(10, w - left - right), plotH = Math.max(10, h - top - bottom);

        // eixos
        g2.setColor(new Color(225, 231, 238));
        for (int i = 0; i <= 4; i++) {
            int y = top + plotH * i / 4;
            g2.drawLine(left, y, left + plotW, y);
        }
        g2.setColor(Theme.MUTED);
        g2.setFont(new Font("SansSerif", 0, 9));
        double max = series.stream().mapToDouble(Series::value).max().orElse(1);
        if (max <= 0) max = 1;
        for (int i = 0; i <= 4; i++) {
            double v = max * (4 - i) / 4.0;
            g2.drawString(shortNum(v), 4, top + plotH * i / 4 + 4);
        }

        if (series.isEmpty()) {
            g2.setColor(Theme.MUTED);
            g2.setFont(new Font("SansSerif", 0, 12));
            g2.drawString("Sem dados no período", left + plotW / 2 - 55, top + plotH / 2);
            return;
        }
        int n = series.size();
        int gap = 8;
        int bw = Math.max(6, (plotW - gap * (n + 1)) / Math.max(1, n));
        int i = 0;
        for (Series s : series) {
            int x = left + gap + i * (bw + gap);
            int bh = (int) ((s.value() / max) * (plotH - 10));
            int y = top + plotH - bh;
            g2.setColor(barColor);
            g2.fillRoundRect(x, y, bw, bh, 8, 8);
            g2.setColor(Theme.MUTED);
            String lbl = s.label();
            int fw = g2.getFontMetrics().stringWidth(lbl);
            g2.drawString(lbl, x + bw / 2 - fw / 2, h - 20);
            i++;
        }
    }

    private String shortNum(double v) {
        if (v >= 1_000_000) return String.format("%.1fM", v / 1_000_000);
        if (v >= 1_000) return String.format("%.0fk", v / 1_000);
        return String.format("%.0f", v);
    }
}
