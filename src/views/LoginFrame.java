package views;

import controllers.AuthController;
import models.User;

import javax.swing.*;
import java.awt.*;

/** Ecrã de autenticação (utilizador, e-mail ou documento do cliente). */
public class LoginFrame extends JFrame {

    private final JTextField username = new JTextField("admin");
    private final JPasswordField password = new JPasswordField("1234567890123");

    public LoginFrame() {
        setTitle("Banco | Acesso ao Sistema");
        setSize(1200, 760);
        setMinimumSize(new Dimension(900, 600));
        setLocationRelativeTo(null);
        setExtendedState(JFrame.MAXIMIZED_BOTH);
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        JPanel root = new JPanel(new GridLayout(1, 2));

        // Painel esquerdo: identidade do banco
        JPanel brand = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                GradientPaint gp = new GradientPaint(0, 0, Theme.NAVY, 0, getHeight(), new Color(15, 28, 50));
                g2.setPaint(gp);
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.dispose();
            }
        };
        brand.setLayout(new BoxLayout(brand, BoxLayout.Y_AXIS));
        brand.setBorder(BorderFactory.createEmptyBorder(70, 60, 60, 60));

        JLabel logo = new JLabel("• BANCO ACADÉMICO");
        logo.setForeground(Color.WHITE);
        logo.setFont(new Font("SansSerif", Font.BOLD, 16));

        JLabel headline = new JLabel(
                "<html><div style='width:380px; line-height: 1.3;'>Gestão bancária simples.<br>Movimentações seguras.</div></html>");
        headline.setForeground(Color.WHITE);
        headline.setFont(new Font("SansSerif", Font.BOLD, 32));

        JLabel note = new JLabel(
                "<html><div style='width:360px; line-height: 1.4;'>Ambiente de simulação académica integrado. "
                + "Desenvolvido para testes práticos e controlo de operações institucionais.</div></html>");
        note.setForeground(new Color(197, 211, 232));
        note.setFont(new Font("SansSerif", Font.PLAIN, 13));

        brand.add(logo);
        brand.add(Box.createVerticalStrut(90));
        brand.add(headline);
        brand.add(Box.createVerticalStrut(20));
        brand.add(note);
        brand.add(Box.createVerticalGlue());

        JLabel footerNote = new JLabel("Módulo de Autenticação • v3.1 — Java Swing Local");
        footerNote.setForeground(new Color(130, 155, 190));
        footerNote.setFont(new Font("SansSerif", Font.PLAIN, 11));
        brand.add(footerNote);

        // Painel direito: formulário
        JPanel formContainer = new JPanel(new GridBagLayout());
        formContainer.setBackground(Theme.BG);

        Theme.RoundedPanel card = Theme.card();
        card.setPreferredSize(new Dimension(420, 480));
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(BorderFactory.createEmptyBorder(35, 40, 35, 40));

        JLabel title = new JLabel("Bem-vindo de volta");
        title.setFont(new Font("SansSerif", Font.BOLD, 22));
        title.setForeground(Theme.NAVY);

        JLabel sub = new JLabel("Insira as suas credenciais para aceder");
        sub.setForeground(Theme.MUTED);
        sub.setFont(new Font("SansSerif", Font.PLAIN, 13));

        card.add(title);
        card.add(Box.createVerticalStrut(4));
        card.add(sub);
        card.add(Box.createVerticalStrut(26));

        card.add(label("Utilizador"));
        card.add(Box.createVerticalStrut(6));
        username.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        styleTextField(username);
        card.add(username);
        card.add(Box.createVerticalStrut(16));

        card.add(label("Palavra-passe"));
        card.add(Box.createVerticalStrut(6));
        password.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        styleTextField(password);
        card.add(password);
        card.add(Box.createVerticalStrut(8));

        JCheckBox showPassword = new JCheckBox("Mostrar palavra-passe");
        showPassword.setOpaque(false);
        showPassword.setForeground(Theme.MUTED);
        showPassword.setFont(new Font("SansSerif", Font.PLAIN, 12));
        showPassword.setAlignmentX(LEFT_ALIGNMENT);
        showPassword.setFocusPainted(false);
        showPassword.addActionListener(e -> password.setEchoChar(showPassword.isSelected() ? (char) 0 : '●'));
        card.add(showPassword);
        card.add(Box.createVerticalStrut(22));

        JButton enter = Theme.button("ENTRAR NO SISTEMA");
        enter.setAlignmentX(LEFT_ALIGNMENT);
        enter.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        enter.setFont(new Font("SansSerif", Font.BOLD, 13));
        enter.addActionListener(e -> login());
        card.add(enter);
        card.add(Box.createVerticalStrut(20));

        JLabel hint = new JLabel(
                "<html><div style='line-height: 1.3;'><b>Contas de demonstração:</b><br>"
                + "• Admin: <font color='#1a5fb4'>admin / 1234567890123</font><br>"
                + "• Funcionário: <font color='#1a5fb4'>func.caixa / Funcionario123!</font><br>"
                + "• Cliente: <font color='#1a5fb4'>joao.silva / Cliente123!</font></div></html>");
        hint.setFont(new Font("SansSerif", Font.PLAIN, 11));
        hint.setForeground(Theme.MUTED);
        hint.setAlignmentX(LEFT_ALIGNMENT);
        card.add(hint);

        formContainer.add(card);

        root.add(brand);
        root.add(formContainer);
        setContentPane(root);

        password.addActionListener(e -> login());
        username.addActionListener(e -> password.requestFocusInWindow());
    }

    private JLabel label(String s) {
        JLabel l = new JLabel(s);
        l.setFont(new Font("SansSerif", Font.BOLD, 12));
        l.setForeground(Theme.INK);
        l.setAlignmentX(LEFT_ALIGNMENT);
        return l;
    }

    private void styleTextField(JComponent c) {
        c.setAlignmentX(LEFT_ALIGNMENT);
        c.setFont(new Font("SansSerif", Font.PLAIN, 13));
        c.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(210, 218, 226), 1),
            BorderFactory.createEmptyBorder(4, 8, 4, 8)
        ));
    }

    private void login() {
        try {
            User u = new AuthController().login(username.getText().trim(), new String(password.getPassword()));
            if (u == null) {
                JOptionPane.showMessageDialog(this, "Credenciais inválidas ou conta inactiva.",
                        "Aviso", JOptionPane.WARNING_MESSAGE);
                return;
            }
            if ("CLIENTE".equals(u.role())) {
                new ClientPortalFrame(u).setVisible(true);
            } else {
                new DashboardFrame(u).setVisible(true);
            }
            dispose();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Erro de autenticação", JOptionPane.ERROR_MESSAGE);
        }
    }
}
