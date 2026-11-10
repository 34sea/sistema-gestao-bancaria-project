package views;

import models.Account;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.function.Supplier;

/**
 * Seleccionador reutilizável de contas: lista combinada (editável — também se
 * pode escrever o número directamente) + botão de actualização. Mostra um
 * marcador claro quando não há contas disponíveis para o utilizador.
 */
public class AccountPicker extends JPanel {

    public static final String NOTHING = "— sem contas disponíveis —";

    private final Supplier<List<Account>> provider;
    private final JComboBox<String> combo = new JComboBox<>();
    private boolean empty = true;

    public AccountPicker(Supplier<List<Account>> provider) {
        this.provider = provider;
        setOpaque(false);
        setLayout(new BorderLayout(6, 0));
        combo.setEditable(true);
        combo.setPreferredSize(new Dimension(240, 36));
        add(combo, BorderLayout.CENTER);
        JButton refresh = Theme.ghost("↻");
        refresh.addActionListener(e -> refresh());
        add(refresh, BorderLayout.EAST);
        refresh();
    }

    /** Número seleccionado (texto digitado ou da lista); null quando vazio/sem contas. */
    public String selectedNumber() {
        Object item = combo.isEditable() && combo.getEditor().getItem() != null
                && !String.valueOf(combo.getEditor().getItem()).isBlank()
                ? combo.getEditor().getItem() : combo.getSelectedItem();
        if (item == null) return null;
        String s = String.valueOf(item).trim();
        if (s.isEmpty() || NOTHING.equals(s)) return null;
        return s;
    }

    public Account selectedAccount() {
        String n = selectedNumber();
        if (n == null) return null;
        for (Account a : provider.get()) if (a.accountNumber.equals(n)) return a;
        return null;
    }

    /** true quando existem contas reais para escolher. */
    public boolean hasAccounts() { return !empty; }

    public void setNumber(String number) {
        combo.setSelectedItem(number);
        combo.getEditor().setItem(number);
    }

    public void refresh() {
        String current = selectedNumber();
        combo.removeAllItems();
        combo.getEditor().setItem("");
        List<Account> list = provider.get();
        empty = list.isEmpty();
        if (empty) {
            combo.addItem(NOTHING);
        } else {
            for (Account a : list) combo.addItem(a.accountNumber);
            if (current != null && !current.isBlank()) {
                if (list.stream().anyMatch(a -> a.accountNumber.equals(current))) {
                    combo.setSelectedItem(current);
                    combo.getEditor().setItem(current);
                }
            } else {
                combo.getEditor().setItem(list.get(0).accountNumber);
            }
        }
    }
}
