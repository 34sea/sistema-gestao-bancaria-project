package views;

import models.User;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Quadro principal do staff (administrador e funcionário): menu lateral
 * + conteúdo em CardLayout (uma "tela" por área funcional).
 */
public class DashboardFrame extends JFrame {

    private final User user;
    private final CardLayout cards = new CardLayout();
    private final JPanel content = new JPanel(cards);
    private final Map<String, JPanel> navToPanel = new LinkedHashMap<>();
    private final JList<String> navList = new JList<>();
    private final JLabel headerTitle = new JLabel();

    public DashboardFrame(User user) {
        this.user = user;
        setTitle("Gestão Bancária — Balcão");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setExtendedState(JFrame.MAXIMIZED_BOTH);
        setMinimumSize(new Dimension(1000, 640));
        setLocationRelativeTo(null);

        // ---------------- cartões de conteúdo ----------------
        boolean admin = user.isAdmin();
        String dashboardKey = admin ? "Painel" : "Painel operacional";
        register(dashboardKey, new DashboardPanel(user, dashboardKey));
        register("Clientes", new ClientsPanel(user));
        if (admin) {
            register("Funcionários", new EmployeesPanel(user));
            register("Utilizadores", new UsersPanel(user));
            register("Auditoria", new AuditPanel(user));
            register("Relatórios", new ReportsPanel());
            register("Tipos de conta", new AccountTypesPanel());
        }
        register("Contas", new AccountsPanel(user));
        register("Depósitos", new DepositPanel(user));
        register("Levantamentos", new WithdrawPanel(user));
        register("Transferências", new TransferPanel(user));
        register("Transacções", new TransactionsPanel(user, false));
        register("Extractos", new StatementsPanel(user));

        // ---------------- barra lateral ----------------
        JPanel sidebar = new JPanel(new BorderLayout());
        sidebar.setBackground(Theme.NAVY);
        sidebar.setPreferredSize(new Dimension(230, 0));

        JPanel brand = new JPanel(new BorderLayout());
        brand.setOpaque(false);
        brand.setBorder(new EmptyBorder(22, 20, 18, 10));
        JLabel logo = new JLabel("• BANCO");
        logo.setForeground(Color.WHITE);
        logo.setFont(new Font("SansSerif", Font.BOLD, 18));
        JLabel sub = new JLabel(admin ? "Console do administrador" : "Balcão do funcionário");
        sub.setForeground(new Color(150, 170, 200));
        sub.setFont(new Font("SansSerif", Font.PLAIN, 11));
        JPanel brandStack = new JPanel(new GridLayout(2, 1, 0, 2));
        brandStack.setOpaque(false);
        brandStack.add(logo);
        brandStack.add(sub);
        brand.add(brandStack, BorderLayout.WEST);

        List<String> navItems = List.copyOf(navToPanel.keySet());
        navList.setListData(navItems.toArray(new String[0]));
        navList.setBackground(Theme.NAVY);
        navList.setForeground(new Color(205, 218, 236));
        navList.setSelectionBackground(Theme.NAVY_2);
        navList.setSelectionForeground(Color.WHITE);
        navList.setFont(new Font("SansSerif", Font.BOLD, 13));
        navList.setBorder(new EmptyBorder(6, 6, 6, 6));
        navList.setFixedCellHeight(42);
        navList.setCellRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> l, Object v, int i, boolean sel, boolean foc) {
                Component c = super.getListCellRendererComponent(l, v, i, sel, foc);
                if (c instanceof JLabel jLabel) {
                    jLabel.setBorder(new EmptyBorder(0, 14, 0, 6));
                    jLabel.setHorizontalAlignment(SwingConstants.LEFT);
                }
                return c;
            }
        });
        navList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) show(navList.getSelectedValue());
        });
        JScrollPane navScroll = new JScrollPane(navList);
        navScroll.setBorder(null);
        navScroll.getViewport().setBackground(Theme.NAVY);

        JButton logout = Theme.button("TERMINAR SESSÃO", Theme.NAVY_2);
        logout.setOpaque(true);
        logout.addActionListener(e -> {
            new LoginFrame().setVisible(true);
            dispose();
        });
        JPanel bottom = new JPanel(new GridLayout(1, 1));
        bottom.setOpaque(false);
        bottom.setBorder(new EmptyBorder(16, 20, 20, 20));
        bottom.add(logout);

        sidebar.add(brand, BorderLayout.NORTH);
        sidebar.add(navScroll, BorderLayout.CENTER);
        sidebar.add(bottom, BorderLayout.SOUTH);

        // ---------------- cabeçalho + conteúdo ----------------
        JPanel center = new JPanel(new BorderLayout(0, 0));
        center.setBackground(Theme.BG);

        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(Color.WHITE);
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER),
                new EmptyBorder(14, 24, 14, 24)));
        headerTitle.setFont(new Font("SansSerif", Font.BOLD, 18));
        headerTitle.setForeground(Theme.NAVY);
        JLabel who = new JLabel(user.fullName() + "  •  " + user.role());
        who.setForeground(Theme.MUTED);
        who.setFont(new Font("SansSerif", Font.PLAIN, 12));
        header.add(headerTitle, BorderLayout.WEST);
        header.add(who, BorderLayout.EAST);

        content.setBorder(new EmptyBorder(18, 22, 22, 22));
        center.add(header, BorderLayout.NORTH);
        center.add(content, BorderLayout.CENTER);

        JPanel root = new JPanel(new BorderLayout());
        root.add(sidebar, BorderLayout.WEST);
        root.add(center, BorderLayout.CENTER);
        setContentPane(root);

        navList.setSelectedIndex(0);
    }

    private void register(String navTitle, JPanel panel) {
        navToPanel.put(navTitle, panel);
        content.add(panel, navTitle);
    }

    private void show(String navTitle) {
        JPanel panel = navToPanel.get(navTitle);
        if (panel == null) return;
        headerTitle.setText(navTitle);
        if (panel instanceof Refreshable r) r.onShow();
        cards.show(content, navTitle);
    }

    /** Actualização de dados ao abrir uma "tela". */
    public interface Refreshable { void onShow(); }

    /** Utilizado quando noutra tela há alterações e o "Painel" precisa de refrescar. */
    void refreshAll() {
        for (JPanel p : navToPanel.values()) {
            if (p instanceof Refreshable r) r.onShow();
        }
    }
}
