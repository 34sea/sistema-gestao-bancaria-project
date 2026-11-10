package views;

import controllers.BankingController;
import models.User;
import services.BankingService;
import utils.Money;

import javax.swing.*;
import java.awt.*;
import java.awt.GridBagConstraints;
import java.math.BigDecimal;
import java.util.UUID;

/** Transferência entre contas (funcionários — qualquer conta; clientes — só as próprias). */
public class TransferPanel extends JPanel implements DashboardFrame.Refreshable {

    private final User user;
    private final BankingController banking = new BankingController();
    private final controllers.AccountController accounts = new controllers.AccountController();

    private final AccountPicker source = new AccountPicker(this::sourceAccounts);
    private final JTextField target = Theme.field(16);
    private final JTextField amount = Theme.field(12);
    private final JTextField description = Theme.field(20);
    private final JLabel beneficiary = new JLabel(" ");
    private BankingService.Beneficiary verified;

    public TransferPanel(User user) {
        this.user = user;
        setLayout(new BorderLayout(0, 12));
        setOpaque(false);

        JPanel card = Theme.card(14);
        card.setLayout(new BorderLayout(0, 14));

        JLabel title = new JLabel("Transferência entre contas");
        title.setFont(new Font("SansSerif", Font.BOLD, 15));
        title.setForeground(Theme.NAVY);
        card.add(title, BorderLayout.NORTH);

        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        form.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0; gbc.gridy = 0; gbc.anchor = GridBagConstraints.WEST;
        gbc.weightx = 1; gbc.fill = GridBagConstraints.HORIZONTAL;

        form.add(Theme.formRow("Conta de origem *", source), gbc);
        gbc.gridy++;
        form.add(verifyRow(), gbc);
        gbc.gridy++;
        form.add(Theme.formRow("Valor *", amount), gbc);
        gbc.gridy++;
        form.add(Theme.formRow("Descrição", description), gbc);
        card.add(form, BorderLayout.CENTER);

        beneficiary.setFont(new Font("SansSerif", Font.BOLD, 12));

        JButton submit = Theme.success("TRANSFERIR");
        submit.addActionListener(e -> confirm());
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        actions.setOpaque(false);
        actions.add(submit);
        card.add(actions, BorderLayout.SOUTH);

        card.setPreferredSize(new Dimension(560, 380));
        add(card, BorderLayout.NORTH);
    }

    private JPanel verifyRow() {
        JPanel row = new JPanel(new BorderLayout(8, 4));
        row.setOpaque(false);
        JButton verify = Theme.ghost("VERIFICAR BENEFICIÁRIO");
        verify.addActionListener(e -> verify());
        JPanel inner = new JPanel(new BorderLayout(6, 0));
        inner.setOpaque(false);
        inner.add(verify, BorderLayout.EAST);
        inner.add(beneficiary, BorderLayout.SOUTH);
        row.add(Theme.label("Número da conta de destino"), BorderLayout.NORTH);
        row.add(target, BorderLayout.CENTER);
        row.add(inner, BorderLayout.SOUTH);
        return row;
    }

    private java.util.List<models.Account> sourceAccounts() {
        try {
            return user.hasStaffRights() ? accounts.list(null, "ACTIVA") : accounts.accountsForClient(user);
        } catch (Exception e) { return java.util.List.of(); }
    }

    @Override public void onShow() {
        source.refresh();
        target.setText("");
        amount.setText("");
        description.setText("");
        beneficiary.setText(" ");
        verified = null;
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
            if (srcNo == null || srcNo.isBlank()) throw new exceptions.BusinessException("Escolha a conta de origem.");
            if (verified == null || !dstNo.equals(verified.accountNumber())) {
                int choice = JOptionPane.showConfirmDialog(this,
                        "Beneficiário não verificado ou alterado.\nVerificar antes de transferir?",
                        "Aviso", JOptionPane.OK_CANCEL_OPTION);
                if (choice == JOptionPane.OK_OPTION) { verify(); return; }
                return;
            }
            BigDecimal value = Money.parse(amount.getText());
            int ok = JOptionPane.showConfirmDialog(this,
                    "Origem:        " + srcNo + " (" + verifiedFor(srcNo) + ")"
                            + "\nDestino:      " + dstNo + " (" + verified.holderName() + ")"
                            + "\nValor:          " + Money.format(value)
                            + "\nDescrição:   " + (description.getText().isBlank() ? "-" : description.getText().trim()),
                    "Confirmar transferência", JOptionPane.OK_CANCEL_OPTION);
            if (ok != JOptionPane.OK_OPTION) return;

            BankingService.TransferResult r = banking.transfer(user, srcNo, dstNo, value,
                    description.getText().trim(), null);
            JTextArea area = new JTextArea(
                    "Transferência concluída!\nCódigo: " + r.code()
                            + "\nOrigem: " + r.sourceNumber() + " → Destino: " + r.targetNumber()
                            + "\nSaldo origem: " + Money.format(r.sourceBefore()) + " → " + Money.format(r.sourceAfter()));
            area.setEditable(false);
            area.setOpaque(false);
            Object[] options = {"GUARDAR PDF", "FECHAR"};
            int choice = JOptionPane.showOptionDialog(this, area, "Comprovativo",
                    JOptionPane.DEFAULT_OPTION, JOptionPane.INFORMATION_MESSAGE, null, options, options[1]);
            if (choice == 0) {
                JFileChooser chooser = new JFileChooser();
                chooser.setSelectedFile(new java.io.File("comprovativo-" + r.code() + ".pdf"));
                if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
                    byte[] pdf = new services.StatementPdfService().receiptOfTransaction(user, r.code());
                    java.nio.file.Files.write(chooser.getSelectedFile().toPath(), pdf);
                }
            }
            onShow();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE);
        }
    }

    private String verifiedFor(String number) {
        try {
            return banking.previewBeneficiary(user, number).holderName();
        } catch (Exception e) { return number; }
    }
}
