package views;

import controllers.EmployeeController;
import models.User;
import repositories.UserRepository;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;

/**
 * Gestão de utilizadores (administrador): criação de utilizadores ADMIN,
 * activação/desactivação e listagem de todas as credenciais do sistema.
 */
public class UsersPanel extends JPanel implements DashboardFrame.Refreshable {

    private final User user;
    private final EmployeeController employees = new EmployeeController();

    private final DefaultTableModel model = new DefaultTableModel(
            new String[]{"ID", "Utilizador", "Nome", "Perfil", "Estado", "Vinculado"}, 0) {
        @Override public boolean isCellEditable(int r, int c) { return false; }
    };
    private final JTable table = new JTable(model);
    private final JTextField username = Theme.field(14);
    private final JTextField fullName = Theme.field(14);
    private final JTextField email = Theme.field(14);
    private final JPasswordField password = Theme.passwordField(14);

    public UsersPanel(User user) {
        this.user = user;
        setLayout(new BorderLayout(14, 0));
        setOpaque(false);

        // ---- criação de utilizador ADMIN
        JPanel createCard = Theme.card(14);
        createCard.setLayout(new BorderLayout(0, 10));
        createCard.setPreferredSize(new Dimension(360, 0));
        JLabel title = new JLabel("Criar utilizador (ADMIN)");
        title.setFont(new Font("SansSerif", Font.BOLD, 15));
        title.setForeground(Theme.NAVY);
        createCard.add(title, BorderLayout.NORTH);

        JPanel form = new JPanel(new GridLayout(0, 1, 6, 6));
        form.setOpaque(false);
        form.add(Theme.formRow("Utilizador *", username));
        form.add(Theme.formRow("Nome completo *", fullName));
        form.add(Theme.formRow("E-mail", email));
        form.add(Theme.formRow("Palavra-passe * (mín. 8)", password));
        JButton create = Theme.danger("CRIAR ADMIN");
        create.addActionListener(e -> createAdmin());
        JPanel createRow = new JPanel(new BorderLayout());
        createRow.setOpaque(false);
        createRow.add(create, BorderLayout.EAST);
        JPanel left = new JPanel(new BorderLayout(0, 12));
        left.setOpaque(false);
        left.add(form, BorderLayout.NORTH);
        left.add(createRow, BorderLayout.SOUTH);
        createCard.add(left, BorderLayout.CENTER);
        createCard.add(Theme.muted("Funcionários são criados em \"Funcionários\". Aqui cria administradores adicionais."), BorderLayout.SOUTH);
        add(createCard, BorderLayout.WEST);

        // ---- tabela de todos os utilizadores
        JPanel listCard = Theme.card(14);
        listCard.setLayout(new BorderLayout(0, 10));
        JLabel listTitle = new JLabel("Todos os utilizadores");
        listTitle.setFont(new Font("SansSerif", Font.BOLD, 15));
        listTitle.setForeground(Theme.NAVY);
        listCard.add(listTitle, BorderLayout.NORTH);

        Theme.styleTable(table);
        table.getColumnModel().getColumn(4).setCellRenderer(new ClientsPanel.StatusColorRenderer());
        listCard.add(Theme.scroll(table), BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        actions.setOpaque(false);
        JButton toggle = Theme.ghost("ACTIVAR / DESACTIVAR");
        toggle.addActionListener(e -> toggle());
        actions.add(toggle);
        JButton reset = Theme.ghost("REPOR PALAVRA-PASSE");
        reset.addActionListener(e -> resetPassword());
        actions.add(reset);
        listCard.add(actions, BorderLayout.SOUTH);
        add(listCard, BorderLayout.CENTER);

        reload();
    }

    private void createAdmin() {
        try {
            long id = employees.createAdmin(user, username.getText(), fullName.getText(), email.getText(),
                    new String(password.getPassword()));
            JOptionPane.showMessageDialog(this, "Utilizador ADMIN criado (ID " + id + ").",
                    "Sucesso", JOptionPane.INFORMATION_MESSAGE);
            username.setText("");
            fullName.setText("");
            email.setText("");
            password.setText("");
            reload();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE);
        }
    }

    private Long selectedId() {
        int r = table.getSelectedRow();
        if (r < 0) return null;
        try { return Long.parseLong(String.valueOf(model.getValueAt(r, 0))); }
        catch (Exception e) { return null; }
    }

    private void toggle() {
        Long id = selectedId();
        if (id == null) {
            JOptionPane.showMessageDialog(this, "Seleccione um utilizador.", "Aviso", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String role = String.valueOf(model.getValueAt(table.getSelectedRow(), 3));
        if ("CLIENTE".equals(role)) {
            JOptionPane.showMessageDialog(this, "O estado dos clientes é gerido em \"Clientes\" (ACTIVO/BLOQUEADO/INACTIVO).",
                    "Aviso", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        String state = String.valueOf(model.getValueAt(table.getSelectedRow(), 4));
        boolean activate = "INACTIVO".equals(state);
        int ok = JOptionPane.showConfirmDialog(this,
                (activate ? "Activar" : "Desactivar") + " o utilizador nº " + id + "?",
                "Confirmar", JOptionPane.OK_CANCEL_OPTION);
        if (ok != JOptionPane.OK_OPTION) return;
        try {
            employees.setActive(user, id, activate);
            reload();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void resetPassword() {
        Long id = selectedId();
        if (id == null) {
            JOptionPane.showMessageDialog(this, "Seleccione um utilizador.", "Aviso", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String np = JOptionPane.showInputDialog(this, "Nova palavra-passe (mínimo 8 caracteres):");
        if (np == null) return;
        try {
            employees.resetPassword(user, id, np);
            JOptionPane.showMessageDialog(this, "Palavra-passe reposta.", "Sucesso", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void reload() {
        try {
            model.setRowCount(0);
            // addUserRows junta credenciais de staff + clientes (listagem completa)
            UserRepository repo = new UserRepository();
            for (String[] row : repo.listRows(null, null)) {
                boolean cliente = "CLIENTE".equals(row[5]);
                model.addRow(new Object[]{row[0], row[1], row[2], row[5], row[4],
                        cliente ? "portal do cliente" : "balcão"});
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE);
        }
    }

    @Override public void onShow() { reload(); }
}
