package views;

import models.User;
import utils.Money;

import javax.swing.*;

import java.util.List;

/** Operação de levantamento em balcão (mesma estrutura do depósito, regras próprias). */
public class WithdrawPanel extends DepositPanel {

    public WithdrawPanel(User user) {
        super(user, "Levantamento em balcão", "CONFIRMAR LEVANTAMENTO");
    }

    @Override protected void confirm() {
        try {
            String number = picker.selectedNumber();
            var acc = accounts.byNumber(number);
            java.math.BigDecimal amount = Money.parse(amountField.getText());
            String holder = acc == null ? "" : acc.clientName;
            String balanceTxt = acc == null ? "" : Money.format(acc.balance);
            int ok = JOptionPane.showConfirmDialog(this,
                    "Confirma levantamento de " + Money.format(amount) + " na conta " + number
                            + (holder.isBlank() ? "" : " (" + holder + ")")
                            + (balanceTxt.isBlank() ? "" : "\nSaldo actual: " + balanceTxt),
                    "Confirmar levantamento", JOptionPane.OK_CANCEL_OPTION);
            if (ok != JOptionPane.OK_OPTION) return;

            var r = banking.withdraw(user, number, amount, noteField.getText());
            showReceipt(r.code(), "Levantamento de " + Money.format(amount) + " na conta " + r.accountNumber()
                    + "\nSaldo anterior: " + Money.format(r.before())
                    + "\nNovo saldo: " + Money.format(r.after()));
            onShow();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE);
        }
    }
}
