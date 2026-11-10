package views;

import models.Account;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.function.Supplier;

/**
 * Seleccionador reutilizável de contas: campo de pesquisa + lista combinada
 * (editável — pode escrever-se o número directamente) + botão actualizar.
 */
public class AccountPicker extends JPanel {

    private final Supplier<List<Account>> provider;
    private final JComboBox<String> combo = new JComboBox<>();

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

    /** Número seleccionado (texto digitado ou da lista). */
    public String selectedNumber() {
        Object item = combo.isEditable() && combo.getEditor().getItem() != null
                && !String.valueOf(combo.getEditor().getItem()).isBlank()
                ? combo.getEditor().getItem() : combo.getSelectedItem();
        return item == null ? null : String.valueOf(item).trim();
    }

    public Account selectedAccount() {
        String n = selectedNumber();
        if (n == null) return null;
        for (Account a : provider.get()) if (a.accountNumber.equals(n)) return a;
        return null;
    }

    public void setNumber(String number) {
        combo.setSelectedItem(number);
        combo.getEditor().setItem(number);
    }

    public void refresh() {
        String current = selectedNumber();
        combo.removeAllItems();
        List<Account> list = provider.get();
        for (Account a : list) combo.addItem(a.accountNumber);
        // procura por titular ao digitar: garante item digitado visível
        if (current != null && !current.isBlank()) {
            combo.setSelectedItem(current);
        }
    }

    public static JComboBox<String> withAll(JComboBox<String> combo) {
        return combo;
    }
}
