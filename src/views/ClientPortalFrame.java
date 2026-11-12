package views;

import controllers.AccountController;
import controllers.ClientController;
import controllers.QueryController;
import models.Account;
import models.Client;
import models.User;
import repositories.NotificationRepository;
import services.BankingService;
import utils.Money;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;

/**
 * Portal do cliente (Swing): contas, transferências, movimentos, extractos,
 * notificações e perfil — em abas.
 */
public class ClientPortalFrame extends JFrame {

    private final User user;
    private final Client me;
    private final AccountController accounts = new AccountController();
    private final QueryController queries = new QueryController();
    private final ClientController clients = new ClientController();
    private final NotificationRepository notifications = new NotificationRepository();

    private JTabbedPane tabs;
    private AccountsTab accountsTab;

    public ClientPortalFrame(User user) {
        this.user = user;
        try {
            this.me = clients.byId(user.clientIdOrZero());
        } catch (Exception e) {
            // demasiado raro (utilizador válido sem cliente associado) — falha aparente depois
            throw new IllegalStateException("Cliente não encontrado para a credencial: " + user.username());
        }
        setTitle("Banco — Portal do Cliente | " + me.fullName);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setExtendedState(JFrame.MAXIMIZED_BOTH);
        setMinimumSize(new Dimension(1000, 640));
        setLocationRelativeTo(null);

        // cabeçalho
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(Theme.NAVY);
        header.setBorder(new EmptyBorder(16, 26, 16, 26));
        JLabel logo = new JLabel("• BANCO | Portal do cliente");
        logo.setForeground(Color.WHITE);
        logo.setFont(new Font("SansSerif", Font.BOLD, 17));
        JLabel who = new JLabel(me.fullName + "  •  " + me.documentNumber);
        who.setForeground(new Color(170, 190, 220));
        who.setFont(new Font("SansSerif", Font.PLAIN, 12));
        header.add(logo, BorderLayout.WEST);
        header.add(who, BorderLayout.EAST);

        JPanel logoutBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 4));
        logoutBar.setOpaque(false);
        JButton logout = Theme.button("SAIR", Theme.NAVY_2);
        logout.addActionListener(e -> {
            new LoginFrame().setVisible(true);
            dispose();
        });
        logoutBar.add(logout);
        header.add(logoutBar, BorderLayout.NORTH);

        tabs = new JTabbedPane();
        accountsTab = new AccountsTab();
        tabs.addTab("As minhas contas", accountsTab);
        tabs.addTab("Transferir", new TransferTab());
        tabs.addTab("Movimentos", new TransactionsPanel(user, true));
        tabs.addTab("Extractos", new StatementsPanel(user, this::myAccounts));
        tabs.addTab("Notificações", new NotificationsTab());
        tabs.addTab("O meu perfil", new ProfileTab());

        // cada aba carrega os seus dados ao ser seleccionada (os ComboBox e
        // tabelas de abas menos usadas ficavam vazios sem isto)
        tabs.addChangeListener(e -> {
            Component sel = tabs.getSelectedComponent();
            if (sel instanceof DashboardFrame.Refreshable r) r.onShow();
        });

        setContentPane(new JPanel(new BorderLayout()));
        getContentPane().add(header, BorderLayout.NORTH);
        getContentPane().add(tabs, BorderLayout.CENTER);
    }

    private List<Account> myAccounts() {
        try { return accounts.accountsForClient(user); }
        catch (Exception e) { return List.of(); }
    }

    // ------------------------------------------------------------ abas

    /** Aba "As minhas contas": saldo total + estatísticas + tabela de contas. */
    private class AccountsTab extends JPanel implements DashboardFrame.Refreshable {
        private final JPanel cards = new JPanel(new GridLayout(0, 4, 12, 12));
        private final DefaultTableModel model = new DefaultTableModel(
                new String[]{"Número", "Tipo", "Saldo", "Estado"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };

        AccountsTab() {
            setLayout(new BorderLayout(0, 14));
            setBorder(new EmptyBorder(18, 22, 18, 22));
            setOpaque(false);
            cards.setOpaque(false);

            JPanel card = Theme.card(14);
            card.setLayout(new BorderLayout(0, 10));
            JLabel t = new JLabel("Movimentos recentes (por conta — veja a aba Movimentos para todos)");
            t.setFont(new Font("SansSerif", Font.BOLD, 13));
            t.setForeground(Theme.NAVY);
            card.add(t, BorderLayout.NORTH);
            JTable table = new JTable(model);
            Theme.styleTable(table);
            table.getColumnModel().getColumn(3).setCellRenderer(new ClientsPanel.StatusColorRenderer());
            card.add(Theme.scroll(table), BorderLayout.CENTER);

            JPanel center = new JPanel(new BorderLayout(0, 14));
            center.setOpaque(false);
            center.add(new JPanel(new BorderLayout()), BorderLayout.SOUTH); // espaçador
            add(cards, BorderLayout.NORTH);
            add(card, BorderLayout.CENTER);
            onShow();
        }

        @Override public void onShow() {
            try {
                List<Account> mine = myAccounts();
                var stats = queries.clientMovementStats(user);

                cards.removeAll();
                java.math.BigDecimal total = java.math.BigDecimal.ZERO;
                for (Account a : mine) if (!"ENCERRADA".equals(a.status)) total = total.add(a.balance);
                cards.add(Theme.metric("Saldo total", Money.format(total), Theme.GREEN));
                cards.add(Theme.metric("Contas", String.valueOf(mine.size()) + " (" + stats.get("movementCount") + " movimentos)", Theme.BLUE));
                cards.add(Theme.metric("Entradas totais", "MZN " + stats.get("totalEntries"), Theme.BLUE));
                cards.add(Theme.metric("Saídas totais", "MZN " + stats.get("totalExits"), Theme.RED));
                cards.revalidate();
                cards.repaint();

                model.setRowCount(0);
                for (Account a : mine) {
                    model.addRow(new Object[]{a.accountNumber, a.accountTypeName, Money.format(a.balance), a.status});
                }
            } catch (Exception e) {
                JOptionPane.showMessageDialog(ClientPortalFrame.this, e.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE);
            }
            updateNotificationsBadge();
        }
    }

    /** Aba "Transferir": origens nas contas próprias + verificação + histórico das próprias transferências. */
    private class TransferTab extends JPanel implements DashboardFrame.Refreshable {
        private final JComboBox<String> source = new JComboBox<>();
        private final JTextField target = Theme.field(16);
        private final JTextField amount = Theme.field(12);
        private final JTextField description = Theme.field(20);
        private final JLabel beneficiary = new JLabel(" ");
        private final JLabel emptySource = new JLabel(" ");
        private BankingService.Beneficiary verified;
        private final DefaultTableModel historyModel = new DefaultTableModel(
                new String[]{"Código", "Origem", "Destino", "Valor", "Estado", "Data/hora", "Descrição"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };

        TransferTab() {
            setLayout(new BorderLayout(0, 14));
            setBorder(new EmptyBorder(18, 22, 18, 22));
            setOpaque(false);

            JPanel card = Theme.card(14);
            card.setLayout(new BorderLayout(0, 12));
            JLabel t = new JLabel("Transferência entre as minhas contas");
            t.setFont(new Font("SansSerif", Font.BOLD, 15));
            t.setForeground(Theme.NAVY);
            emptySource.setFont(new Font("SansSerif", Font.BOLD, 12));
            emptySource.setForeground(Theme.RED);
            JPanel titleStack = new JPanel(new BorderLayout(0, 2));
            titleStack.setOpaque(false);
            titleStack.add(t, BorderLayout.NORTH);
            titleStack.add(emptySource, BorderLayout.SOUTH);
            card.add(titleStack, BorderLayout.NORTH);

            JPanel form = new JPanel(new GridBagLayout());
            form.setOpaque(false);
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.gridx = 0; gbc.gridy = 0; gbc.anchor = GridBagConstraints.WEST;
            gbc.weightx = 1; gbc.fill = GridBagConstraints.HORIZONTAL;
            form.add(Theme.formRow("Conta de origem *", source), gbc);
            gbc.gridy++;
            JPanel targetWrap = new JPanel(new BorderLayout(8, 4));
            targetWrap.setOpaque(false);
            targetWrap.add(Theme.label("Número da conta de destino *"), BorderLayout.NORTH);
            JPanel targetRow = new JPanel(new BorderLayout(8, 0));
            targetRow.setOpaque(false);
            targetRow.add(target, BorderLayout.CENTER);
            JButton verify = Theme.ghost("VERIFICAR BENEFICIÁRIO");
            verify.addActionListener(e -> doVerify());
            targetRow.add(verify, BorderLayout.EAST);
            targetWrap.add(targetRow, BorderLayout.CENTER);
            targetWrap.add(beneficiary, BorderLayout.SOUTH);
            targetRow.setPreferredSize(new Dimension(320, 36));
            form.add(targetWrap, gbc);
            gbc.gridy++;
            form.add(Theme.formRow("Valor *", amount), gbc);
            gbc.gridy++;
            form.add(Theme.formRow("Descrição", description), gbc);
            card.add(form, BorderLayout.CENTER);

            JButton submit = Theme.success("TRANSFERIR");
            submit.addActionListener(e -> confirm());
            JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            actions.setOpaque(false);
            actions.add(submit);
            card.add(actions, BorderLayout.SOUTH);

            card.setPreferredSize(new Dimension(560, 400));
            add(card, BorderLayout.WEST);

            // histórico das transferências do próprio cliente
            JPanel historyCard = Theme.card(14);
            historyCard.setLayout(new BorderLayout(0, 10));
            JLabel hTitle = new JLabel("As minhas transferências");
            hTitle.setFont(new Font("SansSerif", Font.BOLD, 15));
            hTitle.setForeground(Theme.NAVY);
            historyCard.add(hTitle, BorderLayout.NORTH);
            JTable historyTable = new JTable(historyModel);
            Theme.styleTable(historyTable);
            historyTable.getColumnModel().getColumn(4).setCellRenderer(new ClientsPanel.StatusColorRenderer());
            historyCard.add(Theme.scroll(historyTable), BorderLayout.CENTER);
            add(historyCard, BorderLayout.CENTER);
        }

        @Override public void onShow() {
            String current = source.getSelectedItem() == null
                    ? null : String.valueOf(source.getSelectedItem()).split("  •  ")[0].trim();
            List<Account> mine = myAccounts();
            source.removeAllItems();
            for (Account a : mine) source.addItem(a.accountNumber + "  •  " + Money.format(a.balance));
            if (current != null && !current.isBlank()) {
                for (int i = 0; i < source.getItemCount(); i++) {
                    if (source.getItemAt(i).startsWith(current)) { source.setSelectedIndex(i); break; }
                }
            }
            emptySource.setText(mine.isEmpty()
                    ? "Não tem contas disponíveis para transferir. Contacte o seu banco."
                    : " ");
            reloadHistory();
        }

        private void reloadHistory() {
            try {
                historyModel.setRowCount(0);
                for (services.QueryService.TransferLine l : queries.transfersFor(user)) {
                    historyModel.addRow(new Object[]{l.code(), l.sourceAccount(), l.targetAccount(),
                            l.amount(), l.status(), l.date(), l.description()});
                }
                if (historyModel.getRowCount() == 0) {
                    historyModel.addRow(new Object[]{"sem transferências registadas ainda", "", "", "", "", "", ""});
                }
            } catch (Exception e) {
                JOptionPane.showMessageDialog(ClientPortalFrame.this, e.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE);
            }
        }

        private void doVerify() {
            try {
                String number = target.getText().trim();
                verified = new controllers.BankingController().previewBeneficiary(user, number);
                beneficiary.setText("→ " + verified.holderName() + "  •  " + verified.status());
                beneficiary.setForeground("ACTIVA".equals(verified.status()) ? Theme.GREEN : Theme.RED);
            } catch (Exception ex) {
                verified = null;
                beneficiary.setText("✗ " + ex.getMessage());
                beneficiary.setForeground(Theme.RED);
            }
        }

        private String selectedSourceNumber() {
            Object sel = source.getSelectedItem();
            if (sel == null) return null;
            String s = String.valueOf(sel);
            int i = s.indexOf("  •  ");
            return i > 0 ? s.substring(0, i) : s;
        }

        private void confirm() {
            try {
                String src = selectedSourceNumber();
                String dst = target.getText().trim();
                if (src == null) throw new exceptions.BusinessException("Escolha a conta de origem.");
                if (dst.isBlank()) throw new exceptions.BusinessException("Indique a conta de destino.");
                if (verified == null || !dst.equals(verified.accountNumber())) {
                    doVerify();
                    if (verified == null || !dst.equals(verified.accountNumber())) {
                        JOptionPane.showMessageDialog(ClientPortalFrame.this,
                                "Verifique o beneficiário antes de transferir.", "Aviso", JOptionPane.WARNING_MESSAGE);
                        return;
                    }
                }
                java.math.BigDecimal value = utils.Money.parse(amount.getText());
                int ok = JOptionPane.showConfirmDialog(ClientPortalFrame.this,
                        "Origem:      " + src
                                + "\nDestino:     " + dst + " (" + verified.holderName() + ")"
                                + "\nValor:         " + utils.Money.format(value)
                                + "\nDescrição:  " + (description.getText().isBlank() ? "-" : description.getText().trim()),
                        "Confirmar transferência", JOptionPane.OK_CANCEL_OPTION);
                if (ok != JOptionPane.OK_OPTION) return;

                BankingService.TransferResult r = new controllers.BankingController().transfer(
                        user, src, dst, value, description.getText().trim(),
                        java.util.UUID.randomUUID().toString());
                JTextArea area = new JTextArea("Transferência concluída!\nCódigo: " + r.code()
                        + "\nSaldo origem: " + utils.Money.format(r.sourceBefore())
                        + " → " + utils.Money.format(r.sourceAfter()));
                area.setEditable(false);
                area.setOpaque(false);
                Object[] options = {"GUARDAR PDF", "FECHAR"};
                int choice = JOptionPane.showOptionDialog(ClientPortalFrame.this, area, "Comprovativo",
                        JOptionPane.DEFAULT_OPTION, JOptionPane.INFORMATION_MESSAGE, null, options, options[1]);
                if (choice == 0) {
                    JFileChooser chooser = new JFileChooser();
                    chooser.setSelectedFile(new java.io.File("comprovativo-" + r.code() + ".pdf"));
                    if (chooser.showSaveDialog(ClientPortalFrame.this) == JFileChooser.APPROVE_OPTION) {
                        byte[] pdf = new services.StatementPdfService().receiptOfTransaction(user, r.code());
                        java.nio.file.Files.write(chooser.getSelectedFile().toPath(), pdf);
                    }
                }
                amount.setText("");
                description.setText("");
                onShow();
                accountsTab.onShow();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(ClientPortalFrame.this, ex.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    /** Aba "Notificações": lista, marcar lida / todas lidas. */
    private class NotificationsTab extends JPanel implements DashboardFrame.Refreshable {
        private final DefaultListModel<String> listModel = new DefaultListModel<>();
        private final JList<String> list = new JList<>(listModel);
        private List<models.Notification> current = List.of();

        NotificationsTab() {
            setLayout(new BorderLayout(0, 12));
            setBorder(new EmptyBorder(18, 22, 18, 22));
            setOpaque(false);
            JPanel card = Theme.card(14);
            card.setLayout(new BorderLayout(0, 10));
            JLabel t = new JLabel("Notificações");
            t.setFont(new Font("SansSerif", Font.BOLD, 15));
            t.setForeground(Theme.NAVY);
            card.add(t, BorderLayout.NORTH);

            list.setFont(new Font("SansSerif", Font.PLAIN, 13));
            list.setCellRenderer(new DefaultListCellRenderer() {
                @Override public Component getListCellRendererComponent(JList<?> l, Object v, int i, boolean sel, boolean foc) {
                    Component c = super.getListCellRendererComponent(l, v, i, sel, foc);
                    boolean unread = !current.get(i).read();
                    c.setForeground(unread ? Theme.INK : Theme.MUTED);
                    if (unread) c.setFont(new Font("SansSerif", Font.BOLD, 13));
                    return c;
                }
            });
            card.add(Theme.scroll(list), BorderLayout.CENTER);

            JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
            actions.setOpaque(false);
            JButton markOne = Theme.ghost("MARCAR COMO LIDA");
            markOne.addActionListener(e -> markRead());
            actions.add(markOne);
            JButton markAll = Theme.button("MARCAR TODAS COMO LIDAS");
            markAll.addActionListener(e -> markAllRead());
            actions.add(markAll);
            card.add(actions, BorderLayout.SOUTH);
            add(card, BorderLayout.CENTER);
        }

        private int selectedIndex() { return Math.max(0, list.getSelectedIndex()); }

        private void markRead() {
            if (list.getSelectedIndex() < 0) {
                JOptionPane.showMessageDialog(ClientPortalFrame.this, "Escolha uma notificação.",
                        "Aviso", JOptionPane.WARNING_MESSAGE);
                return;
            }
            try {
                new NotificationRepository().markRead(current.get(selectedIndex()).id());
                onShow();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(ClientPortalFrame.this, ex.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE);
            }
        }

        private void markAllRead() {
            try {
                new NotificationRepository().markAllRead(user.id());
                onShow();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(ClientPortalFrame.this, ex.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE);
            }
        }

        @Override public void onShow() {
            try {
                current = notifications.forUser(user.id());
                listModel.clear();
                for (models.Notification n : current) {
                    listModel.addElement("[" + (n.read() ? "lida" : "NOVA") + "] " + n.createdAt() + " • "
                            + n.category() + " • " + n.title() + ": " + n.message());
                }
                updateNotificationsBadge();
            } catch (Exception e) {
                JOptionPane.showMessageDialog(ClientPortalFrame.this, e.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    /** Aba "Perfil": dados de contacto editáveis + alteração de palavra-passe. */
    private class ProfileTab extends JPanel implements DashboardFrame.Refreshable {
        private final JTextField phone = Theme.field(15);
        private final JTextField email = Theme.field(15);
        private final JTextField address = Theme.field(15);
        private final JTextField city = Theme.field(15);
        private final JPasswordField currentPassword = Theme.passwordField(15);
        private final JPasswordField newPassword = Theme.passwordField(15);
        private final JPasswordField newPasswordConfirm = Theme.passwordField(15);

        ProfileTab() {
            setLayout(new BorderLayout(0, 14));
            setBorder(new EmptyBorder(18, 22, 18, 22));
            setOpaque(false);
            JPanel card = Theme.card(14);
            card.setLayout(new BorderLayout(0, 12));
            JLabel t = new JLabel("O meu perfil");
            t.setFont(new Font("SansSerif", Font.BOLD, 15));
            t.setForeground(Theme.NAVY);
            card.add(t, BorderLayout.NORTH);

            JPanel form = new JPanel(new GridLayout(0, 1, 8, 8));
            form.setOpaque(false);
            JLabel info = new JLabel("<html>Nome: <b>" + me.fullName + "</b><br>Documento: " + me.documentNumber
                    + "<br>Utilizador de acesso: " + user.username() + "<br>Estado: " + me.status + "</html>");
            info.setBorder(new EmptyBorder(0, 0, 8, 0));
            form.add(info);
            form.add(Theme.formRow("Telefone", phone));
            form.add(Theme.formRow("E-mail", email));
            form.add(Theme.formRow("Endereço", address));
            form.add(Theme.formRow("Cidade", city));
            JButton save = Theme.button("GUARDAR DADOS DE CONTACTO");
            save.addActionListener(e -> saveContact());
            JPanel saveRow = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            saveRow.setOpaque(false);
            saveRow.add(save);

            JPanel passPanel = new JPanel(new GridLayout(0, 1, 8, 8));
            passPanel.setOpaque(false);
            passPanel.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createTitledBorder("Alterar palavra-passe"),
                    new EmptyBorder(4, 10, 6, 10)));
            passPanel.add(Theme.formRow("Palavra-passe actual", currentPassword));
            passPanel.add(Theme.formRow("Nova palavra-passe (" + utils.Validation.passwordRequirements() + ")", newPassword));
            passPanel.add(Theme.formRow("Confirmar nova palavra-passe", newPasswordConfirm));
            JButton savePass = Theme.ghost("ALTERAR PALAVRA-PASSE");
            savePass.addActionListener(e -> changePassword());
            JPanel passRow = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            passRow.setOpaque(false);
            passRow.add(savePass);

            JPanel right = new JPanel(new BorderLayout(0, 10));
            right.setOpaque(false);
            right.add(saveRow, BorderLayout.SOUTH);
            JPanel left = new JPanel(new BorderLayout(0, 10));
            left.setOpaque(false);
            left.add(form, BorderLayout.NORTH);
            left.add(right, BorderLayout.SOUTH);

            add(passPanel, BorderLayout.NORTH);
            add(left, BorderLayout.CENTER);
            card.add(passPanel, BorderLayout.SOUTH);
            onShow();
        }

        private void saveContact() {
            java.util.List<String> errors = new java.util.ArrayList<>();
            FormDialogs.markInvalid(phone, false);
            FormDialogs.markInvalid(email, false);
            try { utils.Validation.normalizePhone(phone.getText()); }
            catch (Exception ex) { errors.add(ex.getMessage()); FormDialogs.markInvalid(phone, true); }
            try { utils.Validation.normalizeEmail(email.getText()); }
            catch (Exception ex) { errors.add(ex.getMessage()); FormDialogs.markInvalid(email, true); }
            if (FormDialogs.showErrors(ClientPortalFrame.this, errors)) return;
            try {
                clients.updateOwn(user, phone.getText(), email.getText(), address.getText(), city.getText());
                JOptionPane.showMessageDialog(ClientPortalFrame.this, "Dados actualizados.",
                        "Sucesso", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(ClientPortalFrame.this, ex.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE);
            }
        }

        private void changePassword() {
            java.util.List<String> errors = new java.util.ArrayList<>();
            String cur = new String(currentPassword.getPassword());
            String next = new String(newPassword.getPassword());
            for (String i : utils.Validation.passwordIssues(next)) errors.add("A nova palavra-passe não cumpre: " + i);
            if (!next.equals(new String(newPasswordConfirm.getPassword()))) {
                errors.add("A confirmação da nova palavra-passe não coincide.");
            }
            if (next.equals(cur) && !cur.isEmpty()) {
                errors.add("A nova palavra-passe deve ser diferente da actual.");
            }
            if (FormDialogs.showErrors(ClientPortalFrame.this, errors)) return;
            try {
                new controllers.AuthController().changePassword(user, cur, next);
                currentPassword.setText("");
                newPassword.setText("");
                newPasswordConfirm.setText("");
                JOptionPane.showMessageDialog(ClientPortalFrame.this, "Palavra-passe alterada.",
                        "Sucesso", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(ClientPortalFrame.this, ex.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE);
            }
        }

        @Override public void onShow() {
            try {
                Client c = clients.byId(user.clientIdOrZero());
                if (c != null) {
                    phone.setText(c.phone);
                    email.setText(c.email);
                    address.setText(c.address);
                    city.setText(c.city);
                }
            } catch (Exception ignored) {}
        }
    }

    private void updateNotificationsBadge() {
        for (int i = 0; i < tabs.getTabCount(); i++) {
            if ("Notificações".equals(tabs.getTitleAt(i))) {
                long unread = notifications.unreadCount(user.id());
                tabs.setTitleAt(i, unread > 0 ? "Notificações (" + unread + ")" : "Notificações");
            }
        }
    }
}
