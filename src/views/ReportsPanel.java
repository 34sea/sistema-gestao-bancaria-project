package views;

import controllers.QueryController;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;

/** Relatórios: séries diárias dos últimos 14 dias + distribuição de contas. */
public class ReportsPanel extends JPanel {

    private final QueryController queries = new QueryController();

    public ReportsPanel() {
        setLayout(new GridLayout(2, 2, 14, 14));
        setOpaque(false);

        BarChartPanel deposits = new BarChartPanel(Theme.BLUE);
        BarChartPanel withdrawals = new BarChartPanel(Theme.RED);
        BarChartPanel transfers = new BarChartPanel(Theme.GREEN);
        BarChartPanel volume = new BarChartPanel(Theme.NAVY);

        add(chartCard("Depósitos diários (últimos 14 dias)", deposits));
        add(chartCard("Levantamentos diários (últimos 14 dias)", withdrawals));
        add(chartCard("Transferências enviadas (últimos 14 dias)", transfers));
        add(chartCard("Volume total diário (últimos 14 dias)", volume));

        try {
            var series = queries.dailySeriesByType();
            deposits.setSeries(toSeries(series.get("DEPOSITO"), 1));
            withdrawals.setSeries(toSeries(series.get("LEVANTAMENTO"), 1));
            transfers.setSeries(toSeries(series.get("TRANSFERENCIA_ENVIADA"), 1));
            volume.setSeries(toSeries(queries.dailyVolume(), 2)); // volume = soma na 3ª coluna
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE);
        }
    }

    private List<BarChartPanel.Series> toSeries(List<String[]> rows, int columnIndex) {
        List<BarChartPanel.Series> out = new java.util.ArrayList<>();
        for (String[] r : rows) {
            String lbl = r[0].substring(r[0].length() - 5);
            out.add(new BarChartPanel.Series(lbl, parse(r[columnIndex])));
        }
        return out;
    }

    private double parse(String formatted) {
        try {
            return Double.parseDouble(formatted.replace(" MZN", "").replace(".", "").replace(",", "."));
        } catch (Exception e) { return 0; }
    }

    private JPanel chartCard(String title, JComponent chart) {
        JPanel card = Theme.card(14);
        card.setLayout(new BorderLayout());
        JLabel l = new JLabel(title);
        l.setFont(new Font("SansSerif", Font.BOLD, 13));
        l.setForeground(Theme.NAVY);
        l.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));
        card.add(l, BorderLayout.NORTH);
        card.add(chart, BorderLayout.CENTER);
        return card;
    }
}
