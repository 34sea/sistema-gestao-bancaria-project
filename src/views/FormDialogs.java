package views;

import utils.Validation;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Apoio aos formulários: apresentação de erros (uma lista clara de tudo o
 * que está inválido) e diálogo de definição de nova palavra-passe com
 * confirmação e requisitos visíveis. Usado pelos painéis de funcionários,
 * utilizadores e clientes.
 */
final class FormDialogs {

    private FormDialogs() {}

    /** Mostra a lista de problemas de validação; true se houve erros (submissão bloqueada). */
    static boolean showErrors(Component owner, List<String> errors) {
        if (errors.isEmpty()) return false;
        JTextArea area = new JTextArea(String.join("\n", errors));
        area.setEditable(false);
        area.setOpaque(false);
        area.setFont(new Font("SansSerif", Font.PLAIN, 13));
        JOptionPane.showMessageDialog(owner, area, "Verifique os dados", JOptionPane.WARNING_MESSAGE);
        return true;
    }

    /** Marca visualmente um campo inválido (fundo vermelho claro). */
    static void markInvalid(JComponent field, boolean invalid) {
        field.setBackground(invalid ? Theme.RED_BG : Color.WHITE);
    }

    /**
     * Diálogo modal para definir/repor palavra-passe (nova + confirmação).
     * Só fecha com valor válido ou cancelamento; devolve a palavra-passe ou null.
     */
    static String askNewPassword(Component owner, String title) {
        JPasswordField pass = Theme.passwordField(18);
        JPasswordField confirm = Theme.passwordField(18);
        JLabel hint = Theme.muted(Validation.passwordRequirements());
        hint.setBorder(new EmptyBorder(2, 0, 8, 0));

        JPanel form = new JPanel(new GridLayout(0, 1, 6, 6));
        form.setBorder(new EmptyBorder(6, 6, 2, 6));
        form.setOpaque(false);
        form.add(new JLabel("Nova palavra-passe:"));
        form.add(pass);
        form.add(new JLabel("Confirmar palavra-passe:"));
        form.add(confirm);
        form.add(hint);

        while (true) {
            int ok = JOptionPane.showConfirmDialog(owner, form, title,
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (ok != JOptionPane.OK_OPTION) return null;
            String p = new String(pass.getPassword());
            String c = new String(confirm.getPassword());
            List<String> errors = new ArrayList<>(Validation.passwordIssues(p).stream()
                    .map(i -> "A palavra-passe não cumpre: " + i).toList());
            if (!p.equals(c)) errors.add("A confirmação não coincide com a palavra-passe.");
            if (!FormDialogs.showErrors(owner, errors)) return p;
        }
    }
}
