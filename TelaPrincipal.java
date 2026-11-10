import javax.swing.*;
import java.awt.*;

public class TelaPrincipal extends JFrame {

    public TelaPrincipal() {
        setTitle("Tela Principal");
        setSize(400, 250);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        // Criar painel
        JPanel painel = new JPanel();
        painel.setLayout(new FlowLayout());

        // Criar componentes
        JLabel titulo = new JLabel("Bem-vindo à primeira tela!");
        JButton btnAvancar = new JButton("Ir para próxima tela");

        // Ação do botão
        btnAvancar.addActionListener(e -> {
            new SegundaTela();
            dispose(); // Fecha a tela atual
        });

        // Adicionar componentes ao painel
        painel.add(titulo);
        painel.add(btnAvancar);

        // Adicionar painel à janela
        add(painel);

        // Mostrar janela
        setVisible(true);
    }
}