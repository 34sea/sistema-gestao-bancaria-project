package services;

import exceptions.BusinessException;
import models.User;
import repositories.UserRepository;
import utils.Passwords;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Dados de demonstração idempotentes: criados uma única vez na primeira
 * inicialização (marcador "demo.marker" guardado nos utilizadores). Usa os
 * MESMOS serviços do sistema para garantir que saldos, movimentos, notificações
 * e auditoria ficam consistentes desde o início.
 */
public final class DemoDataSeeder {
    private DemoDataSeeder() {}

    private static final String MARKER_USERNAME = "demo.marker";

    public static void seedOnce() throws Exception {
        UserRepository users = new UserRepository();
        if (users.existsUsername(MARKER_USERNAME)) return;   // já inicializado
        if (users.existsUsername("func.caixa") || users.existsUsername("joao.silva")) {
            System.err.println("[demo] Foi detectada uma semeadura parcial. Os dados existentes foram preservados; "
                    + "apague a pasta data/ para recomeçar do zero.");
            return;
        }
        System.out.println("[demo] Sem dados de demonstração — a criar cenário inicial...");
        create(users);
        System.out.println("[demo] Dados de demonstração criados com sucesso.");
    }

    private static void create(UserRepository users) throws Exception {
        final User admin = ensureAdmin(users);

        var emps = new EmployeeService();
        emps.create(admin, "func.caixa", "Maria Lopes", "maria.lopes@demo.ac.mz",
                "Funcionario123!", "FUNCIONARIO", "CAIXA");
        emps.create(admin, "func.gestor", "Tomás Antunes", "tomas.antunes@demo.ac.mz",
                "Funcionario123!", "FUNCIONARIO", "ATENDIMENTO");

        var clients = new ClientService();
        var accountService = new AccountService();
        var banking = new BankingService();

        List<Long> clientIds = new ArrayList<>();
        clientIds.add(clients.register(admin, "João Manuel Silva", "BI001100777AA-DM", LocalDate.of(1988, 3, 14),
                "MASCULINO", "Av. dos Engenhos 45", "Maputo", "+258 84 111 0001", "joao.silva@demo.ac.mz",
                "joao.silva", "Cliente123!"));
        clientIds.add(clients.register(admin, "Maria José Chissano", "BI002200888CC-LM", LocalDate.of(1992, 7, 2),
                "FEMININO", "Rua da Missão 12", "Matola", "+258 84 111 0002", "maria.chissano@demo.ac.mz",
                "maria.chissano", "Cliente123!"));
        clientIds.add(clients.register(admin, "Carlos Alberto Mabjaia", "BI003300999EE-ZB", LocalDate.of(1985, 11, 30),
                "MASCULINO", "Praça 25 de Junho 3", "Beira", "+258 84 111 0003", "carlos.mabjaia@demo.ac.mz",
                "carlos.mabjaia", "Cliente123!"));
        clientIds.add(clients.register(admin, "Ana Cristina Sitoe", "BI004400111FF-QT", LocalDate.of(1996, 1, 21),
                "FEMININO", "Bairro Polana Caniço 88", "Maputo", "+258 84 111 0004", "ana.sitoe@demo.ac.mz",
                "ana.sitoe", "Cliente123!"));
        clientIds.add(clients.register(admin, "Rui Filipe Ngoenha", "BI005500222GG-KP", LocalDate.of(1979, 9, 9),
                "MASCULINO", "Estrada Nacional 2, km 5", "Nampula", "+258 84 111 0005", "rui.ngoenha@demo.ac.mz",
                "rui.ngoenha", "Cliente123!"));

        List<String> correntes = new ArrayList<>();
        List<String> poupancas = new ArrayList<>();
        for (Long id : clientIds) correntes.add(accountService.open(admin, id, "CORRENTE"));
        poupancas.add(accountService.open(admin, clientIds.get(0), "POUPANCA"));
        poupancas.add(accountService.open(admin, clientIds.get(2), "POUPANCA"));

        deposit(banking, admin, correntes.get(0), "25000.00", "Depósito inicial de demonstração");
        deposit(banking, admin, correntes.get(1), "8000.00", "Depósito inicial de demonstração");
        deposit(banking, admin, correntes.get(2), "120000.00", "Depósito inicial de demonstração");
        deposit(banking, admin, correntes.get(3), "3500.00", "Depósito inicial de demonstração");
        deposit(banking, admin, correntes.get(4), "45000.00", "Depósito inicial de demonstração");
        deposit(banking, admin, poupancas.get(0), "5000.00", "Abertura de poupança");
        deposit(banking, admin, poupancas.get(1), "15000.00", "Abertura de poupança");

        withdraw(banking, admin, correntes.get(0), "3000.00", "Levantamento de demonstração");
        withdraw(banking, admin, correntes.get(2), "15000.00", "Levantamento de demonstração");
        withdraw(banking, admin, correntes.get(4), "5000.00", "Levantamento de demonstração");

        // transferências de demonstração entre clientes
        banking.transfer(admin, correntes.get(2), correntes.get(0), new BigDecimal("7500.00"), "Partilha de despesas", null);
        banking.transfer(admin, correntes.get(0), correntes.get(1), new BigDecimal("1200.00"), "Presente de aniversário", null);
        banking.transfer(admin, correntes.get(4), correntes.get(3), new BigDecimal("2000.00"), "Reembolso", null);

        // marcador de semeadura concluída
        users.create(MARKER_USERNAME, "Marcador de dados de demonstração", null, "np", "ADMIN", null, null, null);
    }

    /** Cria o administrador inicial se ainda não existir (idempotente). */
    private static User ensureAdmin(UserRepository users) throws Exception {
        List<String[]> admins = users.listRows("ADMIN", null);
        if (!admins.isEmpty()) return users.findById(Long.parseLong(admins.get(0)[0]));
        String username = System.getenv().getOrDefault("APP_ADMIN_USER", "admin");
        String password = System.getenv("APP_ADMIN_PASSWORD");
        if (password == null || password.isBlank()) {
            password = "1234567890123"; // valor Local simples para o utilizador demo
        }
        if (password.length() < 12 || password.startsWith("CHANGE_ME")) {
            throw new IllegalStateException("Defina APP_ADMIN_PASSWORD com pelo menos 12 caracteres antes da primeira inicialização.");
        }
        long id = users.create(username, "Administrador do Sistema", null, Passwords.hash(password), "ADMIN", null, null, null);
        return users.findById(id);
    }

    private static void deposit(BankingService b, User a, String no, String v, String note) throws Exception {
        b.deposit(a, no, new BigDecimal(v), note);
    }

    private static void withdraw(BankingService b, User a, String no, String v, String note) throws Exception {
        b.withdraw(a, no, new BigDecimal(v), note);
    }
}
