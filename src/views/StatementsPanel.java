package views;

import controllers.AccountController;
import controllers.QueryController;
import models.User;
import services.QueryService;
import services.StatementPdfService;
import utils.Money;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Supplier;

/** Extracto bancário por conta e período: pré-visualização + gravação em PDF. */
public class StatementsPanel extends JPanel implements DashboardFrame.Refreshable {

    private final User user;
    private final QueryController queries = new QueryController();
    private final Supplier<List<models.Account>> accountProvider;

    private final AccountPicker picker;
    private final DatePicker from;
    private final DatePicker to;
    private final DefaultTableModel model = new DefaultTableModel(
            new String[]{"Data", "Operação", "Valor", "Saldo após", "Estado", "Descrição"}, 0) {
        @Override public boolean isCellEditable(int r, int c) { return false; }
    };
    private final JTable table = new JTable(model);
    private final JLabel initial = new JLabel(" ");
    private final JLabel fin = new JLabel(" ");

    public StatementsPanel(User user) {
        this(user, () -> {
            try { return new AccountController().list(null, null); }
            catch (Exception e) { return List.of(); }
        });
    }

    /** O portal do cliente fornece apenas as contas próprias. */
    public StatementsPanel(User user, Supplier<List<models.Account>> accountProvider) {
        this.user = user;
        this.accountProvider = accountProvider;
        picker = new AccountPicker(accountProvider);
        from = new DatePicker(LocalDate.now().minusDays(30));
        to = new DatePicker(LocalDate.now());

        setLayout(new BorderLayout(0, 12));
        setOpaque(false);

        JPanel card = Theme.card(14);
        card.setLayout(new BorderLayout(0, 12));

        // linha de opções
        JPanel options = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        options.setOpaque(false);
        JPanel accWrap = new JPanel(new BorderLayout(4, 2));
        accWrap.setOpaque(false);
        accWrap.add(new JLabel("Conta *"), BorderLayout.NORTH);
        accWrap.add(picker, BorderLayout.CENTER);
        options.add(accWrap);
        options.add(labelled("De", from));
        options.add(labelled("Até", to));

        JButton generate = Theme.button("VER EXTRACTO");
        generate.addActionListener(e -> preview());
        options.add(generate);
        JButton pdf = Theme.success("GUARDAR PDF");
        pdf.addActionListener(e -> savePdf());
        options.add(pdf);

        card.add(options, BorderLayout.NORTH);

        // saldos
        JPanel balances = new JPanel(new GridLayout(1, 2, 12, 6));
        balances.setOpaque(false);
        initial.setFont(new Font("SansSerif", Font.BOLD, 13));
        initial.setForeground(Theme.NAVY);
        fin.setFont(new Font("SansSerif", Font.BOLD, 13));
        fin.setForeground(Theme.NAVY);
        balances.add(initial);
        balances.add(fin);

        Theme.styleTable(table);
        JPanel center = new JPanel(new BorderLayout(0, 8));
        center.setOpaque(false);
        center.add(balances, BorderLayout.NORTH);
        center.add(Theme.scroll(table), BorderLayout.CENTER);
        card.add(center, BorderLayout.CENTER);

        add(card, BorderLayout.CENTER);
    }

    private JComponent labelled(String text, JComponent comp) {
        JPanel w = new JPanel(new BorderLayout(3, 3));
        w.setOpaque(false);
        w.add(new JLabel(text), BorderLayout.NORTH);
        w.add(comp, BorderLayout.CENTER);
        w.setPreferredSize(new Dimension(comp.getPreferredSize().width + 6, 56));
        return w;
    }

    @Override public void onShow() {
        picker.refresh();
    }

    private void preview() {
        String number = picker.selectedNumber();
        if (number == null) {
            JOptionPane.showMessageDialog(this, "Escolha uma conta para emitir o extracto.",
                    "Aviso", JOptionPane.WARNING_MESSAGE);
            return;
        }
        LocalDate f = from.getDate(), d = to.getDate();
        try {
            if (f != null && d != null && f.isAfter(d)) {
                throw new exceptions.BusinessException("A data inicial não pode ser posterior à final.");
            }
            QueryService.StatementData last = queries.statement(user, number, f, d);
            initial.setText("Saldo inicial (antes de " + (f == null ? "histórico" : f) + "): "
                    + Money.format(last.initialBalance()));
            fin.setText("Saldo final do período: " + Money.format(last.finalBalance()));
            model.setRowCount(0);
            for (QueryService.Row r : last.rows()) {
                String signed = ("ENTRADA".equals(r.direction()) ? "+" : "-") + r.amount();
                model.addRow(new Object[]{
                        r.date(),
                        r.type(), signed, r.balanceAfter(), r.status(), r.description()});
            }
            if (last.rows().isEmpty()) {
                JOptionPane.showMessageDialog(this, "Sem movimentos no período escolhido.",
                        "Extracto", JOptionPane.INFORMATION_MESSAGE);
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void savePdf() {
        String number = picker.selectedNumber();
        if (number == null) {
            JOptionPane.showMessageDialog(this, "Escolha uma conta para guardar o extracto PDF.",
                    "Aviso", JOptionPane.WARNING_MESSAGE);
            return;
        }
        LocalDate f = from.getDate(), d = to.getDate();
        try {
            JFileChooser chooser = new JFileChooser();
            chooser.setSelectedFile(new java.io.File("extracto-" + number
                    + (f == null ? "" : "-" + f) + (d == null ? "" : "a" + d) + ".pdf"));
            if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
                byte[] pdf = new StatementPdfService().statement(user, number, f, d);
                java.nio.file.Files.write(chooser.getSelectedFile().toPath(), pdf);
                JOptionPane.showMessageDialog(this, "PDF guardado:\n" + chooser.getSelectedFile().getAbsolutePath(),
                        "Sucesso", JOptionPane.INFORMATION_MESSAGE);
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE);
        }
    }
}
