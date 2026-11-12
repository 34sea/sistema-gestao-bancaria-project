package views;

import controllers.ClientController;
import models.Client;
import models.User;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.time.LocalDate;
import java.util.List;

/** Gestão de clientes: registo (com credencial), pesquisa, edição e estados. */
public class ClientsPanel extends JPanel implements DashboardFrame.Refreshable {

    private final User user;
    private final ClientController clients = new ClientController();

    private final DefaultTableModel model = new DefaultTableModel(
            new String[]{"ID", "Nome completo", "Documento", "Cidade", "Telefone", "Estado"}, 0) {
        @Override public boolean isCellEditable(int r, int c) { return false; }
    };
    private final JTable table = new JTable(model);
    private final JTextField searchField = new JTextField(18);

    public ClientsPanel(User user) {
        this.user = user;
        setLayout(new BorderLayout(12, 12));
        setOpaque(false);

        JPanel left = Theme.card(14);
        left.setLayout(new BorderLayout(0, 12));
        JPanel head = new JPanel(new BorderLayout());
        head.setOpaque(false);
        head.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));
        JLabel t = new JLabel("Clientes registados");
        t.setFont(new Font("SansSerif", Font.BOLD, 15));
        t.setForeground(Theme.NAVY);
        head.add(t, BorderLayout.WEST);
        left.add(head, BorderLayout.NORTH);

        Theme.styleTable(table);
        table.getColumnModel().getColumn(5).setCellRenderer(new StatusColorRenderer());
        left.add(Theme.scroll(table), BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        actions.setOpaque(false);
        JButton register = Theme.button("REGISTAR CLIENTE");
        register.addActionListener(e -> showRegisterDialog());
        actions.add(register);

        JButton edit = Theme.ghost("EDITAR");
        edit.addActionListener(e -> editSelected());
        actions.add(edit);

        JButton statusButton = Theme.ghost("MUDAR ESTADO");
        statusButton.addActionListener(e -> changeStatusSelected());
        actions.add(statusButton);

        left.add(actions, BorderLayout.SOUTH);

        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        JPanel searchRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        searchRow.setOpaque(false);
        searchRow.add(new JLabel("Pesquisar:"));
        searchRow.add(searchField);
        JButton go = Theme.button("PROCURAR");
        go.addActionListener(e -> reload());
        searchRow.add(go);
        top.add(searchRow, BorderLayout.EAST);

        add(top, BorderLayout.NORTH);
        add(left, BorderLayout.CENTER);
        reload();
    }

    @Override public void onShow() { reload(); }

    private void reload() {
        try {
            model.setRowCount(0);
            List<Client> list = clients.search(searchField.getText());
            for (Client c : list) {
                model.addRow(new Object[]{
                        c.id, c.fullName, c.documentNumber, c.city, c.phone, c.status});
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE);
        }
    }

    private Client selected() {
        int r = table.getSelectedRow();
        if (r < 0) return null;
        long id = Long.parseLong(String.valueOf(model.getValueAt(r, 0)));
        try { return clients.byId(id); } catch (Exception e) { return null; }
    }

    private void editSelected() {
        Client c = selected();
        if (c == null) {
            JOptionPane.showMessageDialog(this, "Seleccione um cliente na tabela.", "Aviso", JOptionPane.WARNING_MESSAGE);
            return;
        }
        new ClientDialog(this, user, c).setVisible(true);
        reload();
    }

    private void changeStatusSelected() {
        Client c = selected();
        if (c == null) {
            JOptionPane.showMessageDialog(this, "Seleccione um cliente na tabela.", "Aviso", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String next = switch (c.status == null ? "" : c.status) {
            case "ACTIVO" -> "BLOQUEADO";
            case "BLOQUEADO" -> "INACTIVO";
            default -> "ACTIVO";
        };
        int ok = JOptionPane.showConfirmDialog(this,
                "Mudar estado de \"" + c.fullName + "\" para " + next + "?",
                "Confirmar", JOptionPane.OK_CANCEL_OPTION);
        if (ok != JOptionPane.OK_OPTION) return;
        try {
            clients.setStatus(user, c.id, next);
            reload();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void showRegisterDialog() {
        new ClientDialog(this, user, null).setVisible(true);
        reload();
    }

    /** Coloração do campo estado. */
    static class StatusColorRenderer extends DefaultTableCellRenderer {
        @Override public Component getTableCellRendererComponent(JTable t, Object v, boolean s, boolean f, int r, int c) {
            JLabel l = (JLabel) super.getTableCellRendererComponent(t, v, s, f, r, c);
            String st = String.valueOf(v);
            l.setHorizontalAlignment(SwingConstants.CENTER);
            if ("ACTIVO".equals(st)) l.setForeground(Theme.GREEN);
            else if ("BLOQUEADO".equals(st)) l.setForeground(Theme.RED);
            else l.setForeground(Theme.MUTED);
            return l;
        }
    }

    /** Diálogo de registo / edição de cliente (mesma janela para os dois fluxos). */
    static class ClientDialog extends JDialog {
        ClientDialog(Component owner, User actor, Client existing) {
            setTitle(existing == null ? "Registar novo cliente" : "Editar cliente: " + existing.fullName);
            setModal(true);
            setResizable(false);
            setSize(500, existing == null ? 680 : 560);
            setLocationRelativeTo(owner);

            JTextField fullName = Theme.field(20);
            JTextField documentNumber = Theme.field(20);
            DatePicker birthDate = new DatePicker();
            JComboBox<String> gender = Theme.combo(new String[]{"MASCULINO", "FEMININO"});
            JTextField address = Theme.field(20);
            JTextField city = Theme.field(15);
            JTextField phone = Theme.field(15);
            JTextField email = Theme.field(20);
            JTextField username = Theme.field(15);
            JPasswordField password = Theme.passwordField(15);
            JPasswordField passwordConfirm = Theme.passwordField(15);

            JPanel form = new JPanel(new GridLayout(0, 1, 6, 6));
            form.setOpaque(false);
            form.add(Theme.formRow("Nome completo *", fullName));
            form.add(Theme.formRow("Nº de identificação (BI) *", documentNumber));
            form.add(Theme.formRow("Data de nascimento (escolha no calendário)", birthDate));
            form.add(Theme.formRow("Género", gender));
            form.add(Theme.formRow("Endereço", address));
            form.add(Theme.formRow("Cidade", city));
            form.add(Theme.formRow("Celular * (82–88; aceita +258)", phone));
            form.add(Theme.formRow("E-mail", email));
            if (existing == null) {
                form.add(Theme.formRow("Utilizador de acesso (portal) *", username));
                form.add(Theme.formRow("Palavra-passe * (" + utils.Validation.passwordRequirements() + ")", password));
                form.add(Theme.formRow("Confirmar palavra-passe *", passwordConfirm));
            }

            if (existing != null) {
                fullName.setText(existing.fullName);
                documentNumber.setText(existing.documentNumber);
                birthDate.setDate(existing.birthDate);
                gender.setSelectedItem(existing.gender);
                address.setText(existing.address);
                city.setText(existing.city);
                phone.setText(existing.phone);
                email.setText(existing.email);
            }

            JPanel root = new JPanel(new BorderLayout(0, 14));
            root.setBorder(BorderFactory.createEmptyBorder(18, 18, 18, 18));
            root.add(form, BorderLayout.CENTER);

            JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            buttons.setOpaque(false);
            JButton cancel = Theme.ghost("CANCELAR");
            cancel.addActionListener(e -> dispose());
            JButton save = Theme.button(existing == null ? "REGISTAR" : "GUARDAR");
            save.addActionListener(e -> {
                java.util.List<String> errors = new java.util.ArrayList<>();
                for (JComponent f : new JComponent[]{fullName, documentNumber, phone, email, username})
                    FormDialogs.markInvalid(f, false);

                // ---- validação frontal (mensagens específicas; bloqueia a submissão)
                try { utils.Validation.requireName(fullName.getText(), "O nome completo"); }
                catch (Exception ex) { errors.add(ex.getMessage()); FormDialogs.markInvalid(fullName, true); }
                try { utils.Validation.document(documentNumber.getText()); }
                catch (Exception ex) { errors.add(ex.getMessage()); FormDialogs.markInvalid(documentNumber, true); }
                if (birthDate.hasInvalidText()) {
                    errors.add("Data de nascimento inválida — escolha no calendário ou use AAAA-MM-DD.");
                }
                try { utils.Validation.requirePhone(phone.getText()); }
                catch (Exception ex) { errors.add(ex.getMessage()); FormDialogs.markInvalid(phone, true); }
                try { utils.Validation.normalizeEmail(email.getText()); }
                catch (Exception ex) { errors.add(ex.getMessage()); FormDialogs.markInvalid(email, true); }

                String passPlain = null;
                if (existing == null) {
                    try { utils.Validation.username(username.getText()); }
                    catch (Exception ex) { errors.add(ex.getMessage()); FormDialogs.markInvalid(username, true); }
                    passPlain = new String(password.getPassword());
                    List<String> passIssues = utils.Validation.passwordIssues(passPlain);
                    for (String i : passIssues) errors.add("A palavra-passe não cumpre: " + i);
                    if (!passPlain.equals(new String(passwordConfirm.getPassword()))) {
                        errors.add("A confirmação da palavra-passe não coincide com a palavra-passe.");
                        FormDialogs.markInvalid(passwordConfirm, true);
                    }
                }

                if (FormDialogs.showErrors(this, errors)) return;

                // ---- submissão (o serviço revalida no backend)
                try {
                    LocalDate bd = birthDate.getDate();
                    ClientController cc = new ClientController();
                    if (existing == null) {
                        long n = cc.register(actor, fullName.getText(), documentNumber.getText(), bd,
                                (String) gender.getSelectedItem(), address.getText(), city.getText(),
                                phone.getText(), email.getText(), username.getText(), passPlain);
                        JOptionPane.showMessageDialog(this,
                                "Cliente registado (nº " + n + ").", "Sucesso", JOptionPane.INFORMATION_MESSAGE);
                    } else {
                        cc.update(actor, existing.id, fullName.getText(), documentNumber.getText(), bd,
                                (String) gender.getSelectedItem(), address.getText(), city.getText(),
                                phone.getText(), email.getText());
                        JOptionPane.showMessageDialog(this, "Cliente actualizado.",
                                "Sucesso", JOptionPane.INFORMATION_MESSAGE);
                    }
                    dispose();
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(this, ex.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE);
                }
            });
            buttons.add(cancel);
            buttons.add(save);
            root.add(buttons, BorderLayout.SOUTH);
            setContentPane(root);
        }
    }
}
