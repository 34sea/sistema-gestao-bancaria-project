package views;

import controllers.QueryController;
import models.User;
import repositories.AuditRepository;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;

/** Registo de auditoria (apenas administrador): últimos registos filtráveis por ação. */
public class AuditPanel extends JPanel implements DashboardFrame.Refreshable {

    private final DefaultTableModel model = new DefaultTableModel(
            new String[]{"ID", "Data/hora", "Utilizador", "Perfil", "Acção", "Entidade", "Resultado", "Detalhes"}, 0) {
        @Override public boolean isCellEditable(int r, int c) { return false; }
    };
    private final JTable table = new JTable(model);
    private final JComboBox<String> action = Theme.combo(new String[]{"TODAS", "LOGIN", "DEPOSITO", "LEVANTAMENTO",
            "TRANSFERENCIA", "ABERTURA_CONTA", "ESTADO_CONTA", "ESTADO_CLIENTE", "REGISTAR_CLIENTE",
            "CRIAR_FUNCIONARIO", "ACTIVAR_USER", "DESACTIVAR_USER", "REPOR_PASSWORD", "APLICAR_JUROS"});

    public AuditPanel(User user) {
        if (!user.isAdmin()) {
            throw new IllegalStateException("Painel de auditoria apenas para administradores.");
        }
        setLayout(new BorderLayout(0, 12));
        setOpaque(false);

        JPanel card = Theme.card(14);
        card.setLayout(new BorderLayout(0, 10));
        JLabel title = new JLabel("Registo de auditoria (últimos 300)");
        title.setFont(new Font("SansSerif", Font.BOLD, 15));
        title.setForeground(Theme.NAVY);
        card.add(title, BorderLayout.NORTH);

        JPanel filters = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        filters.setOpaque(false);
        filters.add(new JLabel("Acção:"));
        filters.add(action);
        JButton go = Theme.button("FILTRAR");
        go.addActionListener(e -> reload());
        filters.add(go);
        card.add(filters, BorderLayout.CENTER);

        Theme.styleTable(table);
        card.add(Theme.scroll(table), BorderLayout.CENTER);
        add(card, BorderLayout.CENTER);
        reload();
    }

    private void reload() {
        try {
            model.setRowCount(0);
            String filter = String.valueOf(action.getSelectedItem());
            for (models.AuditLog a : new AuditRepository().recent(300, filter)) {
                model.addRow(new Object[]{
                        a.id(), a.createdAt() == null ? "" : a.createdAt().toString().replace('T', ' '),
                        a.username(), a.role(), a.action(), a.entity() + " " + nvl(a.entityId()),
                        a.result(), nvl(a.details())});
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE);
        }
    }

    private String nvl(String v) { return v == null ? "" : v; }

    @Override public void onShow() { reload(); }
}
