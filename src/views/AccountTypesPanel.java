package views;

import models.AccountType;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;

/** Tipos de conta (configuração da simulação): taxas e operações permitidas. */
public class AccountTypesPanel extends JPanel {

    public AccountTypesPanel() {
        setLayout(new BorderLayout(0, 12));
        setOpaque(false);

        JPanel card = Theme.card(14);
        card.setLayout(new BorderLayout(0, 10));
        JLabel title = new JLabel("Tipos de conta");
        title.setFont(new Font("SansSerif", Font.BOLD, 15));
        title.setForeground(Theme.NAVY);
        card.add(title, BorderLayout.NORTH);

        DefaultTableModel model = new DefaultTableModel(
                new String[]{"Código", "Nome", "Taxa mensal de juros", "Permite levantamento", "Permite transferência"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        JTable table = new JTable(model);
        Theme.styleTable(table);
        for (AccountType t : AccountType.TYPES) {
            model.addRow(new Object[]{t.code(), t.name(), t.ratePercent(),
                    t.allowsWithdrawal() ? "Sim" : "Não", t.allowsTransfer() ? "Sim" : "Não"});
        }
        card.add(Theme.scroll(table), BorderLayout.CENTER);

        JLabel note = Theme.muted("Os juros de poupança são aplicados manualmente pelo administrador (Painel → Contas → Aplicar juros).");
        card.add(note, BorderLayout.SOUTH);

        add(card, BorderLayout.CENTER);
    }
}
