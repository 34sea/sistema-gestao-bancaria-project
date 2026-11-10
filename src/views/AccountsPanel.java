package views;

import controllers.AccountController;
import controllers.ClientController;
import models.Account;
import models.User;
import utils.Money;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;

/**
 * Gestão de contas: abertura (cliente + tipo), bloquear/desbloquear/encerrar
 * e (apenas administrador) aplicação de juros às contas poupança.
 */
public class AccountsPanel extends JPanel implements DashboardFrame.Refreshable {

    private final User user;
    private final AccountController accounts = new AccountController();
    private final ClientController clients = new ClientController();

    private final DefaultTableModel model = new DefaultTableModel(
            new String[]{"Número", "Titular", "Tipo", "Saldo", "Estado"}, 0) {
        @Override public boolean isCellEditable(int r, int c) { return false; }
    };
    private final JTable table = new JTable(model);
    private final JTextField searchField = new JTextField(14);
    private final JComboBox<String> status = Theme.combo(new String[]{"TODOS", "ACTIVA", "BLOQUEADA", "ENCERRADA"});
    private final JComboBox<String> clientCombo = new JComboBox<>();
    private final JComboBox<String> typeCombo = Theme.combo(new String[]{"CORRENTE", "POUPANCA"});

    public AccountsPanel(User user) {
        this.user = user;
        setLayout(new BorderLayout(12, 0));
        setOpaque(false);

        // ---- abertura de conta
        JPanel openCard = Theme.card(14);
        openCard.setLayout(new BorderLayout(0, 10));
        openCard.setPreferredSize(new Dimension(340, 0));
        JLabel title = new JLabel("Abrir nova conta");
        title.setFont(new Font("SansSerif", Font.BOLD, 15));
        title.setForeground(Theme.NAVY);
        openCard.add(title, BorderLayout.NORTH);

        JPanel form = new JPanel(new GridLayout(0, 1, 6, 6));
        form.setOpaque(false);
        JPanel clientWrap = new JPanel(new BorderLayout(4, 2));
        clientWrap.setOpaque(false);
        clientWrap.add(new JLabel("Cliente *"), BorderLayout.NORTH);
        clientCombo.setPreferredSize(new Dimension(220, 34));
        JPanel withRefresh = new JPanel(new BorderLayout(4, 0));
        withRefresh.setOpaque(false);
        withRefresh.add(clientCombo, BorderLayout.CENTER);
        JButton loadClients = Theme.ghost("↻");
        loadClients.addActionListener(e -> loadClients());
        withRefresh.add(loadClients, BorderLayout.EAST);
        clientWrap.add(withRefresh, BorderLayout.CENTER);
        form.add(clientWrap);
        form.add(Theme.formRow("Tipo de conta *", typeCombo));

        JButton open = Theme.button("ABRIR CONTA");
        open.addActionListener(e -> openAccount());
        JPanel openRow = new JPanel(new BorderLayout());
        openRow.setOpaque(false);
        openRow.add(open, BorderLayout.EAST);

        JPanel left = new JPanel(new BorderLayout(0, 12));
        left.setOpaque(false);
        left.add(form, BorderLayout.NORTH);
        left.add(openRow, BorderLayout.SOUTH);
        openCard.add(left, BorderLayout.CENTER);

        if (user.isAdmin()) {
            JButton interest = Theme.danger("APLICAR JUROS (POUPANÇA)");
            interest.addActionListener(e -> applyInterest());
            JPanel interestRow = new JPanel(new BorderLayout());
            interestRow.setOpaque(false);
            interestRow.add(interest, BorderLayout.EAST);
            JPanel left2 = new JPanel(new BorderLayout(0, 8));
            left2.setOpaque(false);
            // rearranja: formulário + botões empilhados
            JPanel bottom = new JPanel(new GridLayout(2, 1, 0, 8));
            bottom.setOpaque(false);
            bottom.add(openRow);
            bottom.add(interestRow);
            openCard.remove(left);
            left.remove(openRow);
            left.add(bottom, BorderLayout.SOUTH);
            openCard.add(left, BorderLayout.CENTER);
        }

        add(openCard, BorderLayout.WEST);

        // ---- tabela de contas
        JPanel listCard = Theme.card(14);
        listCard.setLayout(new BorderLayout(0, 10));
        JLabel listTitle = new JLabel("Contas");
        listTitle.setFont(new Font("SansSerif", Font.BOLD, 15));
        listTitle.setForeground(Theme.NAVY);
        listCard.add(listTitle, BorderLayout.NORTH);

        JPanel filters = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        filters.setOpaque(false);
        filters.add(new JLabel("Pesquisar (nº / titular):"));
        filters.add(searchField);
        filters.add(new JLabel("Estado:"));
        filters.add(status);
        JButton go = Theme.button("FILTRAR");
        go.addActionListener(e -> reload());
        filters.add(go);
        listCard.add(filters, BorderLayout.NORTH);

        Theme.styleTable(table);
        table.getColumnModel().getColumn(4).setCellRenderer(new ClientsPanel.StatusColorRenderer());
        listCard.add(Theme.scroll(table), BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        actions.setOpaque(false);
        JButton block = Theme.ghost("BLOQUEAR");
        block.addActionListener(e -> changeStatus("BLOQUEADA"));
        actions.add(block);
        JButton unblock = Theme.ghost(" REACTIVAR");
        unblock.addActionListener(e -> changeStatus("ACTIVA"));
        actions.add(unblock);
        if (user.isAdmin()) {
            JButton close = Theme.danger("ENCERRAR CONTA");
            close.addActionListener(e -> changeStatus("ENCERRADA"));
            actions.add(close);
        }
        listCard.add(actions, BorderLayout.SOUTH);
        add(listCard, BorderLayout.CENTER);

        loadClients();
        reload();
    }

    private void loadClients() {
        try {
            clientCombo.removeAllItems();
            for (models.Client c : clients.search(null)) {
                clientCombo.addItem(c.id + " — " + c.fullName);
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void openAccount() {
        int idx = clientCombo.getSelectedIndex();
        if (idx < 0) {
            JOptionPane.showMessageDialog(this, "Escolha um cliente.", "Aviso", JOptionPane.WARNING_MESSAGE);
            return;
        }
        long clientId = Long.parseLong(String.valueOf(clientCombo.getSelectedItem()).split(" — ")[0]);
        try {
            String number = accounts.open(user, clientId, (String) typeCombo.getSelectedItem());
            JOptionPane.showMessageDialog(this,
                    "Conta criada.\nNúmero: " + number, "Sucesso", JOptionPane.INFORMATION_MESSAGE);
            reload();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void applyInterest() {
        int ok = JOptionPane.showConfirmDialog(this,
                "Aplicar juros do mês às contas poupança activas com saldo positivo?",
                "Confirmar", JOptionPane.OK_CANCEL_OPTION);
        if (ok != JOptionPane.OK_OPTION) return;
        try {
            int n = accounts.applyInterest(user);
            JOptionPane.showMessageDialog(this,
                    "Juros aplicados em " + n + " conta(s).", "Sucesso", JOptionPane.INFORMATION_MESSAGE);
            reload();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void changeStatus(String newStatus) {
        int r = table.getSelectedRow();
        if (r < 0) {
            JOptionPane.showMessageDialog(this, "Seleccione uma conta.", "Aviso", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String number = String.valueOf(model.getValueAt(r, 0));
        int ok = JOptionPane.showConfirmDialog(this,
                "Mudar a conta " + number + " para o estado " + newStatus + "?",
                "Confirmar", JOptionPane.OK_CANCEL_OPTION);
        if (ok != JOptionPane.OK_OPTION) return;
        try {
            accounts.changeStatus(user, number, newStatus);
            reload();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void reload() {
        try {
            model.setRowCount(0);
            List<Account> list = accounts.list(searchField.getText(), String.valueOf(status.getSelectedItem()));
            for (Account a : list) {
                model.addRow(new Object[]{a.accountNumber, a.clientName, a.accountTypeName,
                        Money.format(a.balance), a.status});
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE);
        }
    }

    @Override public void onShow() { reload(); }
}
