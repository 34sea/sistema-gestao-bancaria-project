package views;

import controllers.QueryController;
import models.Transaction;
import models.User;
import services.QueryService;
import services.StatementPdfService;
import utils.Money;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.time.LocalDate;
import java.util.List;

/**
 * Consulta de movimentos com filtros (tipo, estado, conta, período, texto)
 * e paginação. Usada no balcão (todas as contas) e no portal do cliente
 * (apenas as contas próprias — regra de isolamento).
 */
public class TransactionsPanel extends JPanel implements DashboardFrame.Refreshable {

    private static final String[] TYPES = {"TODOS", "DEPOSITO", "LEVANTAMENTO",
            "TRANSFERENCIA_ENVIADA", "TRANSFERENCIA_RECEBIDA", "JUROS"};
    private static final String[] STATUS = {"TODOS", "CONCLUIDA", "PENDENTE", "RECUSADA", "CANCELADA"};

    private final User user;
    private final boolean clientScoped;
    private final QueryController queries = new QueryController();

    private final DefaultTableModel model = new DefaultTableModel(
            new String[]{"Código", "Data", "Tipo", "Valor", "Sentido", "Saldo após", "Estado", "Conta", "Descrição"}, 0) {
        @Override public boolean isCellEditable(int r, int c) { return false; }
    };
    private final JTable table = new JTable(model);
    private final JComboBox<String> type = Theme.combo(TYPES);
    private final JComboBox<String> status = Theme.combo(STATUS);
    private final JComboBox<String> account;
    private final JTextField textSearch = new JTextField(12);
    private final JSpinner from = dateSpinner();
    private final JSpinner to = dateSpinner();
    private final JLabel pageInfo = new JLabel(" ");
    private int page = 1;
    private int total = 0;
    private static final int PAGE_SIZE = 15;
    private QueryService.MovementsPage last;

    public TransactionsPanel(User user, boolean clientScoped) {
        this.user = user;
        this.clientScoped = clientScoped;

        DefaultComboBoxModel<String> accountModel = new DefaultComboBoxModel<>();
        if (clientScoped) {
            accountModel.addElement("TODAS AS CONTAS");
            try {
                for (models.Account a : new controllers.AccountController().accountsForClient(user)) {
                    accountModel.addElement(a.accountNumber);
                }
            } catch (Exception ignored) {}
            account = new JComboBox<>(accountModel);
        } else {
            account = new JComboBox<>();
            account.setEditable(true);
        }

        setLayout(new BorderLayout(0, 12));
        setOpaque(false);

        JPanel card = Theme.card(14);
        card.setLayout(new BorderLayout(0, 10));

        JPanel filters = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        filters.setOpaque(false);
        filters.add(new JLabel("Tipo:"));
        filters.add(type);
        filters.add(new JLabel("Estado:"));
        filters.add(status);
        if (clientScoped) {
            filters.add(new JLabel("Conta:"));
            filters.add(account);
        } else {
            JPanel accWrap = new JPanel(new BorderLayout(4, 4));
            accWrap.setOpaque(false);
            accWrap.add(new JLabel("Conta:"), BorderLayout.NORTH);
            accWrap.add(account, BorderLayout.CENTER);
            accWrap.setPreferredSize(new Dimension(200, 52));
            filters.add(accWrap);
        }
        JPanel fromWrap = dateWrap("De", from);
        JPanel toWrap = dateWrap("Até", to);
        filters.add(fromWrap);
        filters.add(toWrap);
        filters.add(new JLabel("Livres:"));
        filters.add(textSearch);

        JButton go = Theme.button("FILTRAR");
        go.addActionListener(e -> { page = 1; reload(); });
        filters.add(go);

        JButton clear = Theme.ghost("LIMPAR");
        clear.addActionListener(e -> {
            type.setSelectedIndex(0);
            status.setSelectedIndex(0);
            textSearch.setText("");
            from.setValue(toDate(LocalDate.now().minusDays(30)));
            to.setValue(toDate(LocalDate.now()));
            page = 1;
            reload();
        });
        filters.add(clear);

        Theme.styleTable(table);
        card.add(filters, BorderLayout.NORTH);
        card.add(Theme.scroll(table), BorderLayout.CENTER);

        // paginação
        JPanel paging = new JPanel(new BorderLayout());
        paging.setOpaque(false);
        JButton prev = Theme.ghost("‹ ANTERIOR");
        prev.addActionListener(e -> { if (page > 1) { page--; reload(); } });
        JButton next = Theme.ghost("SEGUINTE ›");
        next.addActionListener(e -> reloadNext());
        JPanel pageButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        pageButtons.setOpaque(false);
        pageButtons.add(pageInfo);
        pageButtons.add(prev);
        pageButtons.add(next);
        paging.add(pageButtons, BorderLayout.EAST);

        JPanel bottomBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        bottomBar.setOpaque(false);
        JButton receipt = Theme.ghost("COMPROVATIVO PDF");
        receipt.addActionListener(e -> saveReceipt());
        bottomBar.add(receipt);
        paging.add(bottomBar, BorderLayout.WEST);

        card.add(paging, BorderLayout.SOUTH);
        add(card, BorderLayout.CENTER);

        from.setValue(toDate(LocalDate.now().minusDays(30)));
        to.setValue(toDate(LocalDate.now()));
        reload();

        // botões redondos imitando o tema no fundo branco
        prev.setBackground(Color.WHITE);
    }

    /** Barra de filtros composta no cartão acima. */
    private JPanel dateWrap(String label, JSpinner spinner) {
        JPanel wrap = new JPanel(new BorderLayout(4, 2));
        wrap.setOpaque(false);
        wrap.add(new JLabel(label), BorderLayout.NORTH);
        wrap.add(spinner, BorderLayout.CENTER);
        wrap.setPreferredSize(new Dimension(150, 52));
        return wrap;
    }

    static JSpinner dateSpinner() {
        JSpinner s = new JSpinner(new SpinnerDateModel());
        s.setEditor(new JSpinner.DateEditor(s, "yyyy-MM-dd"));
        Dimension d = new Dimension(130, 30);
        s.setPreferredSize(d);
        return s;
    }

    static java.util.Date toDate(LocalDate d) {
        return d == null ? null : java.util.Date.from(d.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant());
    }

    static LocalDate spinnerDate(JSpinner s) {
        java.util.Date d = (java.util.Date) s.getValue();
        if (d == null) return null;
        return ((java.util.Date) s.getValue()).toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate();
    }

    private void reloadNext() {
        int maxPage = Math.max(1, (int) Math.ceil((double) total / PAGE_SIZE));
        if (page < maxPage) { page++; reload(); }
    }

    public void reload() {
        try {
            String t = (String) type.getSelectedItem();
            String st = (String) status.getSelectedItem();
            String acc = String.valueOf(account.getSelectedItem());
            LocalDate f = spinnerDate(from);
            LocalDate d = spinnerDate(to);

            QueryService.MovementsPage p = clientScoped
                    ? queries.movementsForClient(user, t, st, acc, f, d, page, PAGE_SIZE)
                    : queries.allMovements(t, st, acc, f, d, page, PAGE_SIZE);
            last = p;
            total = p.total();

            String q = textSearch.getText().toLowerCase();
            List<QueryService.Row> rows = p.rows();
            model.setRowCount(0);
            for (QueryService.Row r : rows) {
                if (!q.isBlank()
                        && !(r.description() != null && r.description().toLowerCase().contains(q))
                        && !(r.code() != null && r.code().toLowerCase().contains(q))
                        && !(r.accountNumber() != null && r.accountNumber().toLowerCase().contains(q))) {
                    continue;
                }
                boolean entrada = "ENTRADA".equals(r.direction());
                String signed = (entrada ? "+" : "-") + r.amount();
                model.addRow(new Object[]{
                        r.code(), r.date() == null ? "" : r.date().replace('T', ' '), r.type(),
                        signed, r.direction(), r.balanceAfter(), r.status(), r.accountNumber(), r.description()});
            }
            int maxPage = Math.max(1, (int) Math.ceil((double) total / PAGE_SIZE));
            pageInfo.setText("Página " + (int) Math.min(page, maxPage) + " de " + maxPage + "  •  " + total + " movimentos");
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void saveReceipt() {
        int r = table.getSelectedRow();
        if (r < 0) {
            JOptionPane.showMessageDialog(this, "Seleccione um movimento da tabela.", "Aviso", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String code = String.valueOf(model.getValueAt(r, 0));
        try {
            // garantir que o movimento pertence ao utilizador (isolamento)
            Transaction t = queries.byCode(user, code);
            if (t == null) throw new exceptions.BusinessException("Operação não encontrada.");
            JFileChooser chooser = new JFileChooser();
            chooser.setSelectedFile(new java.io.File("comprovativo-" + code + ".pdf"));
            if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
                byte[] pdf = new StatementPdfService().receiptOfTransaction(user, code);
                java.nio.file.Files.write(chooser.getSelectedFile().toPath(), pdf);
                JOptionPane.showMessageDialog(this, "PDF guardado:\n" + chooser.getSelectedFile().getAbsolutePath(),
                        "Sucesso", JOptionPane.INFORMATION_MESSAGE);
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE);
        }
    }

    @Override public void onShow() { reload(); }

    public Transaction selectedTransaction() {
        int r = table.getSelectedRow();
        if (r < 0) return null;
        try { return queries.byCode(user, String.valueOf(model.getValueAt(r, 0))); }
        catch (Exception e) { return null; }
    }
}
