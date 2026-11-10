package views;

import controllers.BankingController;
import controllers.QueryController;
import models.User;
import services.BankingService;
import services.StatementPdfService;
import utils.Money;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.math.BigDecimal;
import java.util.List;

/**
 * Transferência entre contas (funcionários — qualquer conta; clientes — só as
 * próprias).
 */
public class TransferPanel extends JPanel implements DashboardFrame.Refreshable {

    private final User user;
    private final BankingController banking = new BankingController();
    private final controllers.AccountController accounts = new controllers.AccountController();
    private final QueryController queries = new QueryController();

    private final AccountPicker source;
    private final JTextField target = Theme.field(16);
    private final JTextField amount = Theme.field(12);
    private final JTextField description = Theme.field(20);
    private final JLabel beneficiary = new JLabel(" ");
    private final JLabel emptySourceLabel = new JLabel(" ");
    private BankingService.Beneficiary verified;

    private void addFormRow(JPanel form, JComponent component) {
        component.setAlignmentX(Component.LEFT_ALIGNMENT);

        Dimension preferred = component.getPreferredSize();
        component.setMaximumSize(
                new Dimension(Integer.MAX_VALUE, preferred.height));

        form.add(component);
        form.add(Box.createVerticalStrut(8));
    }

    private final DefaultTableModel historyModel = new DefaultTableModel(
            new String[] { "Código", "Origem", "Destino", "Valor", "Estado", "Data/hora", "Descrição",
                    "Realizada por" },
            0) {
        @Override
        public boolean isCellEditable(int r, int c) {
            return false;
        }
    };
    private final JTable historyTable = new JTable(historyModel);

    public TransferPanel(User user) {
        this.user = user;
        // criado aqui (e não como inicializador de campo) para o utilizador
        // autenticado já estar definido quando as contas são carregadas
        this.source = new AccountPicker(this::sourceAccounts);

        setLayout(new BorderLayout(0, 14));
        setOpaque(false);

        // ---------------- formulário ----------------
        JPanel card = Theme.card(14);
        card.setLayout(new BorderLayout(0, 14));

        JLabel title = new JLabel("Transferência entre contas");
        title.setFont(new Font("SansSerif", Font.BOLD, 15));
        title.setForeground(Theme.NAVY);
        emptySourceLabel.setFont(new Font("SansSerif", Font.BOLD, 12));
        emptySourceLabel.setForeground(Theme.RED);
        JPanel titleStack = new JPanel(new BorderLayout(0, 2));
        titleStack.setOpaque(false);
        titleStack.add(title, BorderLayout.NORTH);
        titleStack.add(emptySourceLabel, BorderLayout.SOUTH);
        card.add(titleStack, BorderLayout.NORTH);

        // JPanel form = new JPanel(new GridLayout(0, 1, 10, 10));
        // form.setOpaque(false);
        // form.add(Theme.formRow("Conta de origem (escolha ou escreva o número) *",
        // source));
        // form.add(Theme.formRow("Número da conta de destino *", target));
        // form.add(endRow());
        // form.add(Theme.formRow("Valor *", amount));
        // form.add(Theme.formRow("Descrição", description));

        JPanel form = new JPanel();
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.setOpaque(false);

        addFormRow(form, Theme.formRow(
                "Conta de origem (escolha ou escreva o número) *", source));

        addFormRow(form, Theme.formRow(
                "Número da conta de destino *", target));

        addFormRow(form, endRow());

        addFormRow(form, Theme.formRow("Valor *", amount));

        addFormRow(form, Theme.formRow("Descrição", description));
        card.add(form, BorderLayout.CENTER);

        JButton submit = Theme.success("TRANSFERIR");
        submit.addActionListener(e -> confirm());
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        actions.setOpaque(false);
        actions.add(submit);
        card.add(actions, BorderLayout.SOUTH);
        // card.setPreferredSize(new Dimension(560, 420));
        card.setPreferredSize(new Dimension(560, 0));
        add(card, BorderLayout.WEST);

        emptySourceLabel.setFont(new Font("SansSerif", Font.BOLD, 12));
        emptySourceLabel.setForeground(Theme.RED);

        // ---------------- histórico de transferências ----------------
        JPanel historyCard = Theme.card(14);
        historyCard.setLayout(new BorderLayout(0, 10));
        JLabel hTitle = new JLabel("Transferências registadas");
        hTitle.setFont(new Font("SansSerif", Font.BOLD, 15));
        hTitle.setForeground(Theme.NAVY);
        historyCard.add(hTitle, BorderLayout.NORTH);

        Theme.styleTable(historyTable);
        historyTable.getColumnModel().getColumn(4).setCellRenderer(new ClientsPanel.StatusColorRenderer());
        historyCard.add(Theme.scroll(historyTable), BorderLayout.CENTER);

        JPanel center = new JPanel(new BorderLayout(0, 14));
        center.setOpaque(false);
        center.add(historyCard, BorderLayout.CENTER);
        add(center, BorderLayout.CENTER);

        onShow();
    }

    private List<models.Account> sourceAccounts() {
        try {
            return user != null && user.hasStaffRights()
                    ? accounts.list(null, "ACTIVA")
                    : accounts.accountsForClient(user);
        } catch (Exception e) {
            return List.of();
        }
    }

    private JPanel endRow() {
        JPanel row = new JPanel(new BorderLayout(8, 4));
        row.setOpaque(false);
        JButton verify = Theme.ghost("VERIFICAR BENEFICIÁRIO");
        verify.addActionListener(e -> verify());
        beneficiary.setFont(new Font("SansSerif", Font.BOLD, 12));
        JPanel top = new JPanel(new BorderLayout(8, 0));
        top.setOpaque(false);
        top.add(verify, BorderLayout.EAST);
        row.add(Theme.label("Beneficiário (verificação antes de transferir)"), BorderLayout.NORTH);
        row.add(top, BorderLayout.CENTER);
        row.add(beneficiary, BorderLayout.SOUTH);
        return row;
    }

    @Override
    public void onShow() {
        source.refresh();
        target.setText("");
        amount.setText("");
        description.setText("");
        beneficiary.setText(" ");
        verified = null;
        emptySourceLabel.setText(source.hasAccounts() ? " " : "Não há contas activas disponíveis.");
        reloadHistory();
    }

    private void reloadHistory() {
        try {
            historyModel.setRowCount(0);
            for (services.QueryService.TransferLine t : queries.transfersFor(user)) {
                historyModel.addRow(new Object[] {
                        t.code(), t.sourceAccount(), t.targetAccount(), t.amount(),
                        t.status(), t.date(), t.description(), t.performedBy() });
            }
            if (historyModel.getRowCount() == 0) {
                historyModel.addRow(new Object[] { "sem transferências registadas ainda", "", "", "", "", "", "", "" });
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void verify() {
        try {
            verified = banking.previewBeneficiary(user, target.getText());
            beneficiary.setText("→ " + verified.holderName() + "  •  " + verified.status());
            beneficiary.setForeground("ACTIVA".equals(verified.status()) ? Theme.GREEN : Theme.RED);
        } catch (Exception ex) {
            verified = null;
            beneficiary.setText("✗ " + ex.getMessage());
            beneficiary.setForeground(Theme.RED);
        }
    }

    private void confirm() {
        try {
            String srcNo = source.selectedNumber();
            String dstNo = target.getText().trim();
            if (srcNo == null || srcNo.isBlank())
                throw new exceptions.BusinessException("Escolha a conta de origem.");
            if (dstNo.isBlank())
                throw new exceptions.BusinessException("Indique a conta de destino.");
            if (verified == null || !dstNo.equals(verified.accountNumber())) {
                verify();
                if (verified == null || !dstNo.equals(verified.accountNumber())) {
                    JOptionPane.showMessageDialog(this,
                            "Verifique o beneficiário antes de transferir.", "Aviso", JOptionPane.WARNING_MESSAGE);
                    return;
                }
            }
            BigDecimal value = Money.parse(amount.getText());
            int ok = JOptionPane.showConfirmDialog(this,
                    "Origem:    " + srcNo
                            + "\nDestino:   " + dstNo + " (" + verified.holderName() + ")"
                            + "\nValor:     " + Money.format(value)
                            + "\nDescrição: " + (description.getText().isBlank() ? "-" : description.getText().trim()),
                    "Confirmar transferência", JOptionPane.OK_CANCEL_OPTION);
            if (ok != JOptionPane.OK_OPTION)
                return;

            BankingService.TransferResult r = banking.transfer(user, srcNo, dstNo, value,
                    description.getText().trim(), java.util.UUID.randomUUID().toString());
            JTextArea area = new JTextArea("Transferência concluída!\nCódigo: " + r.code()
                    + "\nOrigem: " + r.sourceNumber() + " → Destino: " + r.targetNumber()
                    + "\nSaldo origem: " + Money.format(r.sourceBefore()) + " → " + Money.format(r.sourceAfter()));
            area.setEditable(false);
            area.setOpaque(false);
            Object[] options = { "GUARDAR PDF", "FECHAR" };
            int choice = JOptionPane.showOptionDialog(this, area, "Comprovativo",
                    JOptionPane.DEFAULT_OPTION, JOptionPane.INFORMATION_MESSAGE, null, options, options[1]);
            if (choice == 0) {
                JFileChooser chooser = new JFileChooser();
                chooser.setSelectedFile(new java.io.File("comprovativo-" + r.code() + ".pdf"));
                if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
                    byte[] pdf = new StatementPdfService().receiptOfTransaction(user, r.code());
                    java.nio.file.Files.write(chooser.getSelectedFile().toPath(), pdf);
                }
            }
            onShow();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE);
        }
    }
}
