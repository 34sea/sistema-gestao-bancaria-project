package views;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;

/**
 * Campo de selecção de data com calendário visual (popup de mês navegação
 * ◀ ▶). Reutilizado em todos os campos de data da aplicação, para um padrão
 * único: apresentação e aceitação em AAAA-MM-DD, com validação.
 */
public class DatePicker extends JPanel {

    private static final String[] WEEK = {"Seg", "Ter", "Qua", "Qui", "Sex", "Sáb", "Dom"};

    private final JTextField field = new JTextField(10);
    private final JPopupMenu popup = new JPopupMenu();
    private YearMonth shown;
    private LocalDate value;

    public DatePicker() { this(null); }

    public DatePicker(LocalDate initial) {
        setLayout(new BorderLayout(6, 0));
        setOpaque(false);
        field.setFont(new Font("SansSerif", Font.PLAIN, 13));
        field.setColumns(10);
        field.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { fieldChanged(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { fieldChanged(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { fieldChanged(); }
        });
        add(field, BorderLayout.CENTER);
        JButton pick = Theme.ghost("📅");
        pick.setMargin(new Insets(2, 8, 2, 8));
        pick.addActionListener(e -> showCalendar());
        add(pick, BorderLayout.EAST);

        if (initial != null) setDate(initial);
    }

    // -------------------------------------------------------------- API

    /** Data analisada do campo; null quando vazio (texto inválido conta como null). */
    public LocalDate getDate() {
        return value;
    }

    /** true quando o utilizador escreveu algo que não é uma data válida. */
    public boolean hasInvalidText() {
        String t = field.getText().trim();
        return !t.isEmpty() && value == null;
    }

    public void setDate(LocalDate d) {
        field.setText(d == null ? "" : d.toString());
        fieldChanged();
    }

    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        field.setEnabled(enabled);
        for (Component c : getComponents()) c.setEnabled(enabled);
    }

    // -------------------------------------------------------------- interna

    private boolean updatingText = false;

    private void fieldChanged() {
        String t = field.getText().trim();
        if (updatingText) { paintError(false); return; }
        if (t.isEmpty()) { value = null; paintError(false); return; }
        try {
            value = LocalDate.parse(t);
            paintError(false);
        } catch (DateTimeParseException e) {
            value = null;
            paintError(true);
        }
    }

    private void paintError(boolean error) {
        field.setBackground(error ? Theme.RED_BG : Color.WHITE);
    }

    private void showCalendar() {
        shown = value != null ? YearMonth.from(value) : YearMonth.now();
        popup.removeAll();
        popup.setBorder(new EmptyBorder(6, 6, 6, 6));
        popup.add(buildCalendarPanel());
        popup.pack();
        popup.show(this, 0, getHeight());
    }

    private JComponent buildCalendarPanel() {
        JPanel p = new JPanel(new BorderLayout(4, 4));
        p.setBackground(Color.WHITE);

        // cabeçalho: ◀  Mês AAAA  ▶
        JPanel head = new JPanel(new BorderLayout(4, 0));
        head.setOpaque(false);
        JLabel title = new JLabel(monthLabel(shown), SwingConstants.CENTER);
        title.setFont(new Font("SansSerif", Font.BOLD, 13));
        title.setForeground(Theme.NAVY);
        JButton prev = smallGhost("◀");
        prev.addActionListener(e -> { shown = shown.minusMonths(1); rebuild(p); });
        JButton next = smallGhost("▶");
        next.addActionListener(e -> { shown = shown.plusMonths(1); rebuild(p); });
        head.add(prev, BorderLayout.WEST);
        head.add(title, BorderLayout.CENTER);
        head.add(next, BorderLayout.EAST);
        p.add(head, BorderLayout.NORTH);

        p.add(daysGrid(), BorderLayout.CENTER);

        // rodapé: "hoje"
        JPanel foot = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 2));
        foot.setOpaque(false);
        JButton today = smallGhost("hoje");
        today.addActionListener(e -> { setDate(LocalDate.now()); popup.setVisible(false); });
        foot.add(today);
        p.add(foot, BorderLayout.SOUTH);
        return p;
    }

    private JComponent daysGrid() {
        JPanel grid = new JPanel(new GridLayout(0, 7, 2, 2));
        grid.setOpaque(false);
        for (String w : WEEK) {
            JLabel l = new JLabel(w, SwingConstants.CENTER);
            l.setFont(new Font("SansSerif", Font.BOLD, 10));
            l.setForeground(Theme.MUTED);
            grid.add(l);
        }
        // 1ª posição: segunda-feira = 0
        int offset = shown.atDay(1).getDayOfWeek().getValue() - 1;
        for (int i = 0; i < offset; i++) grid.add(new JLabel(""));
        YearMonth ym = shown;
        for (int d = 1; d <= ym.lengthOfMonth(); d++) {
            LocalDate date = ym.atDay(d);
            boolean selected = value != null && value.equals(date);
            JButton b = new JButton(String.valueOf(d)) {
                @Override protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(getBackground());
                    g2.fillRect(0, 0, getWidth(), getHeight());
                    g2.dispose();
                    super.paintComponent(g);
                }
            };
            b.setFont(new Font("SansSerif", selected ? Font.BOLD : 0, 12));
            b.setForeground(selected ? Color.WHITE : Theme.INK);
            b.setBackground(selected ? Theme.BLUE : Color.WHITE);
            b.setContentAreaFilled(false);
            b.setFocusPainted(false);
            b.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(226, 233, 241)),
                    new EmptyBorder(4, 6, 4, 6)));
            b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            b.addActionListener(e -> { setDate(date); popup.setVisible(false); });
            grid.add(b);
        }
        return grid;
    }

    private void rebuild(JPanel panel) {
        popup.removeAll();
        popup.add(buildCalendarPanel());
        popup.pack();
        popup.show(this, 0, getHeight());
        panel.revalidate();
    }

    private JButton smallGhost(String text) {
        JButton b = new JButton(text);
        b.setFont(new Font("SansSerif", Font.BOLD, 11));
        b.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(190, 205, 224)),
                new EmptyBorder(3, 8, 3, 8)));
        b.setContentAreaFilled(false);
        b.setFocusPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setForeground(Theme.BLUE);
        return b;
    }

    private String monthLabel(YearMonth ym) {
        String[] months = {"Janeiro", "Fevereiro", "Março", "Abril", "Maio", "Junho",
                "Julho", "Agosto", "Setembro", "Outubro", "Novembro", "Dezembro"};
        return months[ym.getMonthValue() - 1] + " " + ym.getYear();
    }
}
