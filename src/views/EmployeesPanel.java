package views;

import controllers.EmployeeController;
import models.User;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;

/** Gestão de funcionários (administrador): criar, activar/desactivar, repor senha, pesquisar. */
public class EmployeesPanel extends JPanel implements DashboardFrame.Refreshable {

    private final User user;
    private final EmployeeController employees = new EmployeeController();

    private final DefaultTableModel model = new DefaultTableModel(
            new String[]{"ID utilizador", "Utilizador", "Nome", "E-mail", "Estado", "Departamento"}, 0) {
        @Override public boolean isCellEditable(int r, int c) { return false; }
    };
    private final JTable table = new JTable(model);
    private final JTextField username = Theme.field(14);
    private final JTextField fullName = Theme.field(14);
    private final JTextField email = Theme.field(14);
    private final JPasswordField password = Theme.passwordField(14);
    private final JPasswordField passwordConfirm = Theme.passwordField(14);
    private final JTextField position = Theme.field(14);
    private final JTextField department = Theme.field(14);

    public EmployeesPanel(User user) {
        this.user = user;
        setLayout(new BorderLayout(14, 0));
        setOpaque(false);

        // ---- coluna esquerda: criação
        JPanel createCard = Theme.card(14);
        createCard.setLayout(new BorderLayout(0, 10));
        createCard.setPreferredSize(new Dimension(360, 0));

        JLabel title = new JLabel("Criar funcionário");
        title.setFont(new Font("SansSerif", Font.BOLD, 15));
        title.setForeground(Theme.NAVY);
        createCard.add(title, BorderLayout.NORTH);

        JPanel form = new JPanel(new GridLayout(0, 1, 6, 6));
        form.setOpaque(false);
        form.add(Theme.formRow("Utilizador *", username));
        form.add(Theme.formRow("Nome completo *", fullName));
        form.add(Theme.formRow("E-mail", email));
        form.add(Theme.formRow("Palavra-passe * (" + utils.Validation.passwordRequirements() + ")", password));
        form.add(Theme.formRow("Confirmar palavra-passe *", passwordConfirm));
        form.add(Theme.formRow("Cargo", position));
        form.add(Theme.formRow("Departamento", department));

        JButton create = Theme.button("CRIAR FUNCIONÁRIO");
        create.addActionListener(e -> create());
        JPanel createRow = new JPanel(new BorderLayout());
        createRow.setOpaque(false);
        createRow.add(create, BorderLayout.EAST);

        JPanel left = new JPanel(new BorderLayout(0, 12));
        left.setOpaque(false);
        left.add(form, BorderLayout.NORTH);
        left.add(createRow, BorderLayout.SOUTH);
        createCard.add(left, BorderLayout.CENTER);
        createCard.add(formHint(), BorderLayout.SOUTH);
        add(createCard, BorderLayout.WEST);

        // ---- coluna direita: tabela de funcionários
        JPanel listCard = Theme.card(14);
        listCard.setLayout(new BorderLayout(0, 10));
        JLabel listTitle = new JLabel("Funcionários");
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

    private JLabel formHint() {
        JLabel hint = Theme.muted("O funcionário entra no balcão com o utilizador e palavra-passe definidos aqui.");
        return hint;
    }

    private void create() {
        java.util.List<String> errors = new java.util.ArrayList<>();
        for (JComponent f : new JComponent[]{username, fullName, email, password, passwordConfirm})
            FormDialogs.markInvalid(f, false);
        try { utils.Validation.username(username.getText()); }
        catch (Exception ex) { errors.add(ex.getMessage()); FormDialogs.markInvalid(username, true); }
        try { utils.Validation.requireName(fullName.getText(), "O nome"); }
        catch (Exception ex) { errors.add(ex.getMessage()); FormDialogs.markInvalid(fullName, true); }
        try { utils.Validation.normalizeEmail(email.getText()); }
        catch (Exception ex) { errors.add(ex.getMessage()); FormDialogs.markInvalid(email, true); }
        String passPlain = new String(password.getPassword());
        for (String i : utils.Validation.passwordIssues(passPlain)) errors.add("A palavra-passe não cumpre: " + i);
        if (!passPlain.equals(new String(passwordConfirm.getPassword()))) {
            errors.add("A confirmação da palavra-passe não coincide com a palavra-passe.");
            FormDialogs.markInvalid(passwordConfirm, true);
        }
        if (FormDialogs.showErrors(this, errors)) return;

        try {
            long id = employees.create(user, username.getText(), fullName.getText(), email.getText(),
                    passPlain, position.getText(), department.getText());
            JOptionPane.showMessageDialog(this, "Funcionário criado (ID " + id + ").",
                    "Sucesso", JOptionPane.INFORMATION_MESSAGE);
            username.setText("");
            fullName.setText("");
            email.setText("");
            password.setText("");
            passwordConfirm.setText("");
            position.setText("");
            department.setText("");
            reload();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE);
        }
    }

    private Long selectedId() {
        int r = table.getSelectedRow();
        return r < 0 ? null : Long.parseLong(String.valueOf(model.getValueAt(r, 0)));
    }

    private void toggle() {
        Long id = selectedId();
        if (id == null) {
            JOptionPane.showMessageDialog(this, "Seleccione um funcionário.", "Aviso", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String state = String.valueOf(model.getValueAt(table.getSelectedRow(), 4));
        boolean activate = "INACTIVO".equals(state);
        int ok = JOptionPane.showConfirmDialog(this,
                (activate ? "Activar" : "Desactivar") + " o funcionário nº " + id + "?",
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
            JOptionPane.showMessageDialog(this, "Seleccione um funcionário.", "Aviso", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String np = FormDialogs.askNewPassword(this, "Repor palavra-passe do funcionário");
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
            for (String[] row : employees.list(null)) {
                model.addRow(new Object[]{row[0], row[1], row[2], row[3], row[4], row[6]});
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE);
        }
    }

    @Override public void onShow() { reload(); }
}
