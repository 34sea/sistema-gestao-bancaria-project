import persistence.BankStore;
import services.DemoDataSeeder;
import views.LoginFrame;
import views.Theme;

import javax.swing.*;

/**
 * Aplicação desktop (Java Swing) do Sistema de Gestão Bancária.
 * Demonstra o cenário: administradores, funcionários e um portal para clientes.
 *
 * Arranque: Main -> carrega dados locais (pasta data/) -> semeadura demo (1ª vez) -> LoginFrame.
 */
public class Main {
    public static void main(String[] args) {
        Theme.install();
        try {
            BankStore.get().load();
            DemoDataSeeder.seedOnce();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null,
                    "Não foi possível inicializar os dados locais.\n" + e.getMessage(),
                    "Erro", JOptionPane.ERROR_MESSAGE);
            return;
        }
        SwingUtilities.invokeLater(() -> new LoginFrame().setVisible(true));
    }
}
