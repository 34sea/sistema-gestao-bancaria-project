package views;

import controllers.QueryController;
import models.Transaction;
import models.User;
import services.QueryService;
import services.StatementPdfService;

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

    private static final String ALL_ACCOUNTS = "TODAS AS CONTAS";
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
    private final JComboBox<String> account = new JComboBox<>();
    private final JTextField textSearch = new JTextField(10);
    private final DatePicker from;
    private final DatePicker to;
    private final JLabel pageInfo = new JLabel(" ");
    private int page = 1;
    private int total = 0;
    private static final int PAGE_SIZE = 15;

    public TransactionsPanel(User user, boolean clientScoped) {
        this.user = user;
        this.clientScoped = clientScoped;

        // contas visíveis consoante o perfil (o cliente só as próprias;
        // funcionário/administrador todas) — primeiro item = filtro "todas"
        DefaultComboBoxModel<String> accountModel = new DefaultComboBoxModel<>();
        accountModel.addElement(ALL_ACCOUNTS);
        try {
            List<models.Account> list = clientScoped
                    ? new controllers.AccountController().accountsForClient(user)
                    : new controllers.AccountController().list(null, null);
            for (models.Account a : list) accountModel.addElement(a.accountNumber);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
                    "Não foi possível carregar as contas: " + e.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE);
        }
        account.setModel(accountModel);

        LocalDate today = LocalDate.now();
        from = new DatePicker(today.minusDays(30));
        to = new DatePicker(today);

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
        filters.add(new JLabel("Conta:"));
        account.setPreferredSize(new Dimension(170, 34));
        filters.add(account);
        from.setPreferredSize(new Dimension(210, 34));
        to.setPreferredSize(new Dimension(210, 34));
        filters.add(labelled("De", from));
        filters.add(labelled("Até", to));
        filters.add(new JLabel("Pesquisa livre:"));
        filters.add(textSearch);

        JButton go = Theme.button("FILTRAR");
        go.addActionListener(e -> { page = 1; reload(); });
        filters.add(go);

        JButton clear = Theme.ghost("LIMPAR");
        clear.addActionListener(e -> {
            type.setSelectedIndex(0);
            status.setSelectedIndex(0);
            account.setSelectedIndex(0);
            textSearch.setText("");
            from.setDate(today.minusDays(30));
            to.setDate(today);
            page = 1;
            reload();
        });
        filters.add(clear);

        Theme.styleTable(table);
        card.add(filters, BorderLayout.NORTH);
        card.add(Theme.scroll(table), BorderLayout.CENTER);

        // paginação + comprovativo
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
        reload();
    }

    private JComponent labelled(String text, JComponent comp) {
        JPanel w = new JPanel(new BorderLayout(3, 3));
        w.setOpaque(false);
        w.add(new JLabel(text), BorderLayout.NORTH);
        w.add(comp, BorderLayout.CENTER);
        w.setPreferredSize(new Dimension(comp.getPreferredSize().width + 6, 52));
        return w;
    }

    private void reloadNext() {
        int maxPage = Math.max(1, (int) Math.ceil((double) total / PAGE_SIZE));
        if (page < maxPage) { page++; reload(); }
    }

    public void reload() {
        try {
            String t = (String) type.getSelectedItem();
            String st = (String) status.getSelectedItem();
            String acc = account.getSelectedItem() == null ? ""
                    : String.valueOf(account.getSelectedItem()).trim();
            if (acc.equals(ALL_ACCOUNTS)) acc = "";

            QueryService.MovementsPage p = clientScoped
                    ? queries.movementsForClient(user, t, st, acc, from.getDate(), to.getDate(), page, PAGE_SIZE)
                    : queries.allMovements(t, st, acc, from.getDate(), to.getDate(), page, PAGE_SIZE);
            total = p.total();

            String q = textSearch.getText().toLowerCase();
            model.setRowCount(0);
            for (QueryService.Row r : p.rows()) {
                if (!q.isBlank()
                        && !(r.description() != null && r.description().toLowerCase().contains(q))
                        && !(r.code() != null && r.code().toLowerCase().contains(q))
                        && !(r.accountNumber() != null && r.accountNumber().toLowerCase().contains(q))) {
                    continue;
                }
                boolean entrada = "ENTRADA".equals(r.direction());
                String signed = (entrada ? "+" : "-") + r.amount();
                model.addRow(new Object[]{
                        r.code(), r.date(), r.type(), signed, r.direction(),
                        r.balanceAfter(), r.status(), r.accountNumber(), r.description()});
            }
            int maxPage = Math.max(1, (int) Math.ceil((double) total / PAGE_SIZE));
            pageInfo.setText("Página " + Math.min(page, maxPage) + " de " + maxPage + "  •  "
                    + total + " movimentos" + (p.rows().isEmpty() && q.isBlank() ? "  (sem movimentos nos filtros escolhidos)" : ""));
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

    public void reloadAccountFilter() {
        String current = account.getSelectedItem() == null ? null : String.valueOf(account.getSelectedItem());
        DefaultComboBoxModel<String> m = (DefaultComboBoxModel<String>) account.getModel();
        m.removeAllElements();
        m.addElement(ALL_ACCOUNTS);
        try {
            List<models.Account> list = clientScoped
                    ? new controllers.AccountController().accountsForClient(user)
                    : new controllers.AccountController().list(null, null);
            for (models.Account a : list) m.addElement(a.accountNumber);
        } catch (Exception ignored) {}
        if (current != null) {
            for (int i = 0; i < m.getSize(); i++) {
                if (m.getElementAt(i).equals(current)) { m.setSelectedItem(current); break; }
            }
        }
    }

    public Transaction selectedTransaction() {
        int r = table.getSelectedRow();
        if (r < 0) return null;
        try { return queries.byCode(user, String.valueOf(model.getValueAt(r, 0))); }
        catch (Exception e) { return null; }
    }
}
