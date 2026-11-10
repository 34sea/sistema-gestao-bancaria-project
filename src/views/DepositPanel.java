package views;

import controllers.BankingController;
import models.Account;
import models.User;
import services.BankingService;
import services.StatementPdfService;
import utils.Money;

import javax.swing.*;
import java.awt.*;
import java.math.BigDecimal;
import java.util.List;

/** Operação de depósito em balcão (funcionários e administradores). */
public class DepositPanel extends JPanel implements DashboardFrame.Refreshable {

    protected final User user;
    protected final BankingController banking = new BankingController();
    protected final controllers.AccountController accounts = new controllers.AccountController();
    protected final AccountPicker picker;
    protected final JTextField amountField;
    protected final JTextField noteField;

    public DepositPanel(User user) {
        this(user, "Depósito em balcão", "CONFIRMAR DEPÓSITO");
    }

    protected DepositPanel(User user, String title, String confirmLabel) {
        this.user = user;
        setLayout(new BorderLayout(0, 12));
        setOpaque(false);

        picker = new AccountPicker(this::loadAccounts);
        amountField = new JTextField(12);
        noteField = new JTextField(18);

        JPanel card = Theme.card(14);
        card.setLayout(new BorderLayout(0, 14));

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 15));
        titleLabel.setForeground(Theme.NAVY);
        titleLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0));
        card.add(titleLabel, BorderLayout.NORTH);

        JPanel form = new JPanel(new GridLayout(0, 1, 10, 10));
        form.setOpaque(false);
        form.add(Theme.formRow("Conta (escolha ou escreva o número) *", picker));
        form.add(Theme.formRow("Valor (ex.: 1.500,50 ou 1500.50) *", amountField));
        form.add(Theme.formRow("Descrição / nota (opcional)", noteField));
        card.add(form, BorderLayout.CENTER);

        JButton submit = Theme.success(confirmLabel);
        submit.addActionListener(e -> confirm());
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        actions.setOpaque(false);
        actions.add(submit);
        card.add(actions, BorderLayout.SOUTH);

        card.setPreferredSize(new Dimension(560, 300));
        add(card, BorderLayout.NORTH);
    }

    protected List<Account> loadAccounts() {
        try { return accounts.list(null, "ACTIVA"); }
        catch (Exception e) { return List.of(); }
    }

    @Override public void onShow() {
        picker.refresh();
        amountField.setText("");
        noteField.setText("");
    }

    protected void confirm() {
        try {
            String number = picker.selectedNumber();
            Account acc = accounts.byNumber(number);
            BigDecimal amount = Money.parse(amountField.getText());
            String holder = acc == null ? "" : acc.clientName;
            String balanceTxt = acc == null ? "" : Money.format(acc.balance);
            int ok = JOptionPane.showConfirmDialog(this,
                    "Confirma depósito de " + Money.format(amount) + " na conta " + number
                            + (holder.isBlank() ? "" : " (" + holder + ")")
                            + (balanceTxt.isBlank() ? "" : "\nSaldo actual: " + balanceTxt),
                    "Confirmar depósito", JOptionPane.OK_CANCEL_OPTION);
            if (ok != JOptionPane.OK_OPTION) return;

            BankingService.OperationResult r = banking.deposit(user, number, amount, noteField.getText());
            showReceipt(r.code(), "Depósito de " + Money.format(amount) + " na conta " + r.accountNumber()
                    + "\nSaldo anterior: " + Money.format(r.before())
                    + "\nNovo saldo: " + Money.format(r.after()));
            onShow();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE);
        }
    }

    /** Diálogo de comprovativo com opção "Guardar PDF" (partilhado pelas operações). */
    protected void showReceipt(String code, String summaryLines) {
        JTextArea area = new JTextArea(summaryLines + "\nCódigo: " + code);
        area.setEditable(false);
        area.setOpaque(false);
        area.setFont(new Font("SansSerif", Font.PLAIN, 13));
        Object[] options = {"GUARDAR PDF", "FECHAR"};
        int choice = JOptionPane.showOptionDialog(this, area, "Comprovativo",
                JOptionPane.DEFAULT_OPTION, JOptionPane.INFORMATION_MESSAGE, null, options, options[1]);
        if (choice == 0) savePdf(user, code);
    }

    protected void savePdf(User actor, String code) {
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new java.io.File("comprovativo-" + code + ".pdf"));
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                byte[] pdf = new StatementPdfService().receiptOfTransaction(actor, code);
                java.nio.file.Files.write(chooser.getSelectedFile().toPath(), pdf);
                JOptionPane.showMessageDialog(this, "PDF guardado em:\n"
                        + chooser.getSelectedFile().getAbsolutePath(), "Sucesso", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
}
