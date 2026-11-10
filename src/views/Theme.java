package views;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.table.JTableHeader;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;

/** Identidade visual do banco (azul-escuro/azul/branco + verde/vermelho para estados). */
public final class Theme {
    public static final Color NAVY   = new Color(17, 34, 64);
    public static final Color NAVY_2 = new Color(26, 48, 84);
    public static final Color BLUE   = new Color(35, 100, 186);
    public static final Color BLUE_D = new Color(24, 76, 146);
    public static final Color BG     = new Color(243, 246, 251);
    public static final Color CARD   = Color.WHITE;
    public static final Color BORDER = new Color(224, 231, 240);
    public static final Color INK    = new Color(33, 46, 66);
    public static final Color MUTED  = new Color(110, 124, 144);
    public static final Color GREEN  = new Color(28, 138, 92);
    public static final Color RED    = new Color(200, 52, 56);
    public static final Color AMBER  = new Color(196, 140, 16);
    public static final Color GREEN_BG  = new Color(228, 246, 238);
    public static final Color RED_BG    = new Color(252, 232, 233);
    public static final Color AMBER_BG  = new Color(252, 244, 222);

    private Theme() {}

    public static void install() {
        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); } catch (Exception ignored) {}
        UIManager.put("Panel.background", BG);
        UIManager.put("OptionPane.messageFont", new Font("SansSerif", 0, 13));
        UIManager.put("OptionPane.yesButtonText", "Sim");
        UIManager.put("OptionPane.noButtonText", "Não");
        UIManager.put("OptionPane.cancelButtonText", "Cancelar");
        UIManager.put("TabbedPane.selected", new Color(218, 232, 251));
        UIManager.put("TabbedPane.background", new Color(232, 238, 247));
        UIManager.put("TabbedPane.foreground", NAVY);
        UIManager.put("TabbedPane.contentAreaColor", CARD);
        UIManager.put("TabbedPane.focus", BLUE);
        UIManager.put("TabbedPane.highlight", Color.WHITE);
        UIManager.put("TabbedPane.darkShadow", BORDER);
    }

    // ------------------------------------------------------------- botões

    public static JButton button(String text) { return button(text, BLUE); }

    public static JButton button(String text, Color bg) {
        JButton b = new JButton(text) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(getBackground());
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 10, 10));
                g2.dispose();
                super.paintComponent(g);
            }
        };
        b.setBackground(bg);
        b.setForeground(Color.WHITE);
        b.setFocusPainted(false);
        b.setContentAreaFilled(false);
        b.setOpaque(false);
        b.setBorder(BorderFactory.createEmptyBorder(11, 18, 11, 18));
        b.setFont(new Font("SansSerif", Font.BOLD, 12));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseEntered(java.awt.event.MouseEvent e) {
                if (bg.equals(BLUE)) b.setBackground(BLUE_D);
                b.repaint();
            }
            @Override public void mouseExited(java.awt.event.MouseEvent e) {
                if (bg.equals(BLUE)) b.setBackground(bg);
                b.repaint();
            }
        });
        return b;
    }

    public static JButton ghost(String text) {
        JButton b = button(text, CARD);
        b.setForeground(BLUE);
        b.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(190, 205, 224)),
                BorderFactory.createEmptyBorder(10, 16, 10, 16)));
        return b;
    }

    public static JButton danger(String text) { return button(text, RED); }

    public static JButton success(String text) { return button(text, GREEN); }

    // -------------------------------------------------------------- cartões

    public static RoundedPanel card() { return card(16); }

    public static RoundedPanel card(int radius) {
        RoundedPanel p = new RoundedPanel(radius);
        p.setBackground(CARD);
        Border line = BorderFactory.createLineBorder(BORDER);
        p.setBorder(BorderFactory.createCompoundBorder(line, BorderFactory.createEmptyBorder(18, 18, 18, 18)));
        return p;
    }

    public static class RoundedPanel extends JPanel {
        private final int radius;
        public RoundedPanel(int radius) { this.radius = radius; setOpaque(false); }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(getBackground());
            g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), radius, radius));
            g2.dispose();
            super.paintComponent(g);
        }
    }

    /** Cartão de indicador para dashboards. */
    public static JPanel metric(String label, String value, Color accent) {
        RoundedPanel c = card(14);
        c.setLayout(new BorderLayout(0, 6));
        JLabel t = new JLabel(label.toUpperCase());
        t.setFont(new Font("SansSerif", Font.BOLD, 11));
        t.setForeground(MUTED);
        JLabel v = new JLabel(value);
        v.setFont(new Font("SansSerif", Font.BOLD, 22));
        v.setForeground(NAVY);
        JPanel bar = new JPanel();
        bar.setBackground(accent);
        bar.setPreferredSize(new Dimension(52, 4));
        c.add(t, BorderLayout.NORTH);
        c.add(v, BorderLayout.CENTER);
        c.add(bar, BorderLayout.SOUTH);
        return c;
    }

    // -------------------------------------------------------------- tabelas

    public static void styleTable(JTable t) {
        t.setRowHeight(32);
        t.setFont(new Font("SansSerif", 0, 12));
        t.setForeground(INK);
        t.setGridColor(new Color(236, 241, 247));
        t.setSelectionBackground(new Color(222, 235, 252));
        t.setSelectionForeground(INK);
        t.setFillsViewportHeight(true);
        t.setShowVerticalLines(false);
        JTableHeader h = t.getTableHeader();
        h.setReorderingAllowed(false);
        h.setFont(new Font("SansSerif", Font.BOLD, 12));
        h.setBackground(new Color(238, 243, 250));
        h.setForeground(NAVY);
        h.setPreferredSize(new Dimension(0, 38));
        h.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER));
    }

    public static void statusRenderer(JTable table, int statusColumn) {
        DefaultTableCellRenderer r = new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable tb, Object value, boolean sel, boolean foc, int row, int col) {
                JLabel l = (JLabel) super.getTableCellRendererComponent(tb, value, sel, foc, row, col);
                String s = String.valueOf(value);
                l.setHorizontalAlignment(CENTER);
                switch (s) {
                    case "ACTIVA", "ACTIVO", "CONCLUIDA" -> { l.setForeground(GREEN); l.setBackground(sel ? tb.getSelectionBackground() : GREEN_BG); }
                    case "BLOQUEADA", "BLOQUEADO", "RECUSADA" -> { l.setForeground(RED); l.setBackground(sel ? tb.getSelectionBackground() : RED_BG); }
                    case "PENDENTE" -> { l.setForeground(AMBER); l.setBackground(sel ? tb.getSelectionBackground() : AMBER_BG); }
                    case "ENCERRADA", "INACTIVO", "CANCELADA" -> { l.setForeground(MUTED); l.setBackground(sel ? tb.getSelectionBackground() : new Color(238, 242, 247)); }
                    default -> { l.setForeground(INK); l.setBackground(sel ? tb.getSelectionBackground() : tb.getBackground()); }
                }
                l.setOpaque(true);
                return l;
            }
        };
        table.getColumnModel().getColumn(statusColumn).setCellRenderer(r);
    }

    // --------------------------------------------------------- formulários

    public static JLabel label(String text) {
        JLabel l = new JLabel(text);
        l.setFont(new Font("SansSerif", Font.BOLD, 12));
        l.setForeground(INK);
        return l;
    }

    public static JLabel muted(String text) {
        JLabel l = new JLabel(text);
        l.setFont(new Font("SansSerif", Font.PLAIN, 12));
        l.setForeground(MUTED);
        return l;
    }

    public static JTextField field(int cols) {
        JTextField t = new JTextField(cols);
        t.setPreferredSize(new Dimension(t.getPreferredSize().width, 34));
        t.setFont(new Font("SansSerif", Font.PLAIN, 13));
        return t;
    }

    public static JPasswordField passwordField(int cols) {
        JPasswordField t = new JPasswordField(cols);
        t.setPreferredSize(new Dimension(t.getPreferredSize().width, 34));
        t.setFont(new Font("SansSerif", Font.PLAIN, 13));
        return t;
    }

    public static JComboBox<String> combo(String[] items) {
        JComboBox<String> c = new JComboBox<>(items);
        c.setPreferredSize(new Dimension(180, 34));
        return c;
    }

    public static JPanel formRow(String labelText, JComponent field) {
        JPanel p = new JPanel(new BorderLayout(4, 5));
        p.setOpaque(false);
        p.add(label(labelText), BorderLayout.NORTH);
        p.add(field, BorderLayout.CENTER);
        return p;
    }

    public static JScrollPane scroll(JTable t) {
        JScrollPane s = new JScrollPane(t);
        s.setBorder(BorderFactory.createLineBorder(BORDER));
        s.getViewport().setBackground(Color.WHITE);
        return s;
    }

    public static JScrollPane scroll(Component c) {
        JScrollPane s = new JScrollPane(c);
        s.setBorder(BorderFactory.createLineBorder(BORDER));
        s.getViewport().setBackground(Color.WHITE);
        return s;
    }

    public static void title(JPanel container, String text) {
        JLabel l = new JLabel(text);
        l.setFont(new Font("SansSerif", Font.BOLD, 21));
        l.setForeground(NAVY);
        l.setBorder(BorderFactory.createEmptyBorder(2, 2, 10, 2));
        container.add(l, BorderLayout.NORTH);
    }
}
