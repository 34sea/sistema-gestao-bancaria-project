package views;

import controllers.QueryController;
import models.User;
import services.QueryService;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Painel de indicadores: KPIs + gráficos de barras + distribuição de contas. */
public class DashboardPanel extends JPanel implements DashboardFrame.Refreshable {

    private final User user;
    private final QueryController queries = new QueryController();
    private final JPanel kpiRow = new JPanel(new GridLayout(0, 5, 12, 12));
    private final BarChartPanel depositsChart = new BarChartPanel(Theme.BLUE);
    private final BarChartPanel withdrawalsChart = new BarChartPanel(Theme.RED);
    private final BarChartPanel transfersChart = new BarChartPanel(Theme.GREEN);
    private final BarChartPanel volumeChart = new BarChartPanel(Theme.NAVY);
    private final JTable distribution = new JTable(new DefaultTableModel(
            new String[]{"Tipo de conta", "Contas", "Saldo total"}, 0) {
        public boolean isCellEditable(int r, int c) { return false; }
    });

    public DashboardPanel(User user, String title) {
        this.user = user;
        setLayout(new BorderLayout(0, 14));
        setOpaque(false);

        kpiRow.setOpaque(false);

        // ---------------- gráficos ----------------
        JPanel charts = new JPanel(new GridLayout(2, 2, 14, 14));
        charts.setOpaque(false);

        JPanel c1 = Theme.card(14);
        c1.setLayout(new BorderLayout());
        c1.add(header("Depósitos — últimos 14 dias"), BorderLayout.NORTH);
        c1.add(depositsChart, BorderLayout.CENTER);

        JPanel c2 = Theme.card(14);
        c2.setLayout(new BorderLayout());
        c2.add(header("Levantamentos — últimos 14 dias"), BorderLayout.NORTH);
        c2.add(withdrawalsChart, BorderLayout.CENTER);

        JPanel c3 = Theme.card(14);
        c3.setLayout(new BorderLayout());
        c3.add(header("Transferências enviadas — últimos 14 dias"), BorderLayout.NORTH);
        c3.add(transfersChart, BorderLayout.CENTER);

        JPanel c4 = Theme.card(14);
        c4.setLayout(new BorderLayout());
        c4.add(header("Volume total de movimentos — últimos 14 dias"), BorderLayout.NORTH);
        c4.add(volumeChart, BorderLayout.CENTER);

        charts.add(c1);
        charts.add(c2);
        charts.add(c3);
        charts.add(c4);
        charts.setPreferredSize(new Dimension(10, 430)); // garante área legível aos gráficos

        JPanel bottom = new JPanel(new BorderLayout(14, 14));
        bottom.setOpaque(false);
        JPanel distCard = Theme.card(14);
        distCard.setLayout(new BorderLayout());
        distCard.add(header("Distribuição de contas por tipo"), BorderLayout.NORTH);
        Theme.styleTable(distribution);
        // viewport de altura fixa: impede que a tabela "roube" o vertical dos gráficos
        distribution.setPreferredScrollableViewportSize(new Dimension(120, 118));
        distCard.add(Theme.scroll(distribution), BorderLayout.CENTER);
        bottom.add(distCard, BorderLayout.CENTER);

        JPanel center = new JPanel(new BorderLayout(0, 14));
        center.setOpaque(false);
        center.add(charts, BorderLayout.CENTER);
        center.add(bottom, BorderLayout.SOUTH);

        // tudo dentro de scroll horizontal-fixo: em janelas pequenas os gráficos
        // nunca colapsam — o utilizador navega verticalmente
        JPanel content = new JPanel(new BorderLayout(0, 14));
        content.setOpaque(false);
        content.add(kpiRow, BorderLayout.NORTH);
        content.add(center, BorderLayout.CENTER);
        JScrollPane scroller = new JScrollPane(content);
        scroller.setBorder(null);
        scroller.getVerticalScrollBar().setUnitIncrement(18);
        scroller.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroller.getViewport().setBackground(Theme.BG);
        add(scroller, BorderLayout.CENTER);

        onShow();
    }

    private JLabel header(String text) {
        JLabel l = new JLabel(text);
        l.setFont(new Font("SansSerif", Font.BOLD, 13));
        l.setForeground(Theme.NAVY);
        l.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));
        return l;
    }

    @Override public void onShow() {
        try {
            Map<String, String> s = user.isEmployee()
                    ? queries.employeeDashboardStats(user)
                    : queries.dashboardStats();

            kpiRow.removeAll();
            if (s.containsKey("employees")) {
                // administrador: visão global
                kpiRow.add(Theme.metric("Clientes", s.get("clients"), Theme.BLUE));
                kpiRow.add(Theme.metric("Funcionários", s.get("employees"), Theme.GREEN));
                kpiRow.add(Theme.metric("Contas", s.get("accounts"), Theme.NAVY));
                kpiRow.add(Theme.metric("Saldo total", "MZN " + shortFmt(s.get("totalBalance")), Theme.AMBER));
                kpiRow.add(Theme.metric("Transacções", s.get("transactions"), Theme.MUTED));
                kpiRow.add(Theme.metric("Contas activas", s.get("activeAccounts"), Theme.GREEN));
                kpiRow.add(Theme.metric("Contas bloqueadas", s.get("blockedAccounts"), Theme.RED));
                kpiRow.add(Theme.metric("Contas encerradas", s.get("closedAccounts"), Theme.MUTED));
                kpiRow.add(Theme.metric("Depósitos", "MZN " + shortFmt(s.get("deposits")), Theme.BLUE));
                kpiRow.add(Theme.metric("Levantamentos", "MZN " + shortFmt(s.get("withdrawals")), Theme.RED));
            } else {
                kpiRow.add(Theme.metric("Clientes na instituição", s.get("clients"), Theme.BLUE));
                kpiRow.add(Theme.metric("Contas na instituição", s.get("accounts"), Theme.NAVY));
                kpiRow.add(Theme.metric("Depósitos realizados por si", "MZN " + shortFmt(s.get("deposits")), Theme.BLUE));
                kpiRow.add(Theme.metric("Levantamentos realizados por si", "MZN " + shortFmt(s.get("withdrawals")), Theme.RED));
                kpiRow.add(Theme.metric("Transacções (suas)", s.get("transactions"), Theme.MUTED));
            }
            kpiRow.revalidate();
            kpiRow.repaint();

            // séries diárias
            Map<String, List<String[]>> series = queries.dailySeriesByType();
            depositsChart.setSeries(toSeries(series.get("DEPOSITO"), d -> parseNum(d[1])));
            withdrawalsChart.setSeries(toSeries(series.get("LEVANTAMENTO"), d -> parseNum(d[1])));
            transfersChart.setSeries(toSeries(series.get("TRANSFERENCIA_ENVIADA"), d -> parseNum(d[1])));
            volumeChart.setSeries(toSeries(queries.dailyVolume(), d -> parseNum(d[2])));

            // distribuição
            DefaultTableModel m = (DefaultTableModel) distribution.getModel();
            m.setRowCount(0);
            for (String[] row : queries.accountTypeDistribution()) m.addRow(row);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE);
        }
    }

    private java.text.DecimalFormat nf() {
        java.text.DecimalFormat f = new java.text.DecimalFormat("0", java.text.DecimalFormatSymbols.getInstance(
                new java.util.Locale("pt", "PT")));
        f.setGroupingUsed(false);
        return f;
    }

    private double parseNum(String formatted) {
        try {
            String clean = formatted.replace(" MZN", "").replace(".", "").replace(",", ".");
            return Double.parseDouble(clean);
        } catch (Exception e) { return 0; }
    }

    private String shortFmt(String fmt) {
        double v = parseNum(fmt);
        if (v >= 1_000_000) return String.format("%.1fM", v / 1_000_000);
        if (v >= 1_000) return String.format("%.0fk", v / 1_000);
        return fmt;
    }

    private List<BarChartPanel.Series> toSeries(List<String[]> rows, java.util.function.Function<String[], Double> value) {
        List<BarChartPanel.Series> out = new ArrayList<>();
        for (String[] r : rows) {
            String lbl = r[0].substring(r[0].length() - 5); // MM-DD
            out.add(new BarChartPanel.Series(lbl, value.apply(r)));
        }
        return out;
    }
}

