import javax.swing.*;
import java.awt.*;

public class SegundaTela extends JFrame {

    public SegundaTela() {
        setTitle("Segunda Tela");
        setSize(400, 250);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        JPanel painel = new JPanel();
        painel.setLayout(new FlowLayout());

        JLabel mensagem = new JLabel("Você está na segunda tela!");
        JButton btnVoltar = new JButton("Voltar");

        btnVoltar.addActionListener(e -> {
            new TelaPrincipal();
            dispose();
        });

        painel.add(mensagem);
        painel.add(btnVoltar);

        add(painel);

        setVisible(true);
    }
}