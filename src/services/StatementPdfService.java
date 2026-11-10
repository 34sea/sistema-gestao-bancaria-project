package services;

import exceptions.BusinessException;
import exceptions.ForbiddenException;
import models.Account;
import models.Transaction;
import models.User;
import repositories.AccountRepository;
import repositories.ClientRepository;
import repositories.TransactionRepository;
import utils.Money;
import utils.PdfUtil;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** Geração de PDFs: extractos por período e comprovativos de operações. */
public class StatementPdfService {

    private final AccountRepository accounts = new AccountRepository();
    private final TransactionRepository transactions = new TransactionRepository();
    private final ClientRepository clients = new ClientRepository();

    public static final String BANK_NAME = "Banco";
    public static final String BANK_NOTE = "Simulação académica — não representa valores reais.";

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    /** Extracto completo por conta e período (mesma regra de isolamento do serviço de consultas). */
    public byte[] statement(User actor, String accountNumber, LocalDate from, LocalDate to) throws Exception {
        var data = new QueryService().statement(actor, accountNumber, from, to);
        Account acc = data.account();

        List<PdfUtil.Line> lines = new ArrayList<>();
        lines.add(PdfUtil.Line.title(BANK_NAME));
        lines.add(PdfUtil.Line.text(BANK_NOTE));
        lines.add(PdfUtil.Line.subtitle("EXTRACTO BANCÁRIO"));
        lines.add(PdfUtil.Line.small("Titular: " + acc.clientName));
        lines.add(PdfUtil.Line.small("Conta: " + acc.accountNumber + "  |  Tipo: " + acc.accountTypeName
                + "  |  Estado: " + acc.status));
        lines.add(PdfUtil.Line.small("Período: " + (from == null ? "início" : from) + " a " + (to == null ? "actual" : to)));
        lines.add(PdfUtil.Line.smallBold("Saldo inicial do período: " + Money.format(data.initialBalance())));
        lines.add(PdfUtil.Line.text(""));
        lines.add(PdfUtil.Line.smallBold("Data        Operação                      Valor           Saldo após      Estado"));
        for (QueryService.Row r : data.rows()) {
            lines.add(PdfUtil.Line.small(String.format("%-11s %-29s %-15s %-15s %s",
                    r.date().length() >= 10 ? r.date().substring(0, 10) : r.date(),
                    shortType(r.type()), r.amount(), r.balanceAfter(), r.status())));
            if (r.description() != null) lines.add(PdfUtil.Line.small("    " + r.description()));
        }
        lines.add(PdfUtil.Line.text(""));
        lines.add(PdfUtil.Line.subtitle("Saldo final do período: " + Money.format(data.finalBalance())));
        lines.add(PdfUtil.Line.text(""));
        lines.add(PdfUtil.Line.small("Documento gerado pela simulação académica às " + java.time.LocalDateTime.now().format(TS)));
        return PdfUtil.document(lines);
    }

    /** Comprovativo de depósito / levantamento / transferência / juros por código. */
    public byte[] receiptOfTransaction(User actor, String code) throws Exception {
        Transaction t = new QueryService().byCode(actor, code);
        if (t == null) throw new BusinessException("Operação não encontrada.");
        List<PdfUtil.Line> lines = new ArrayList<>();
        lines.add(PdfUtil.Line.title(BANK_NAME));
        lines.add(PdfUtil.Line.text(BANK_NOTE));
        lines.add(PdfUtil.Line.subtitle("COMPROVATIVO DE " + shortType(t.type())));
        lines.add(PdfUtil.Line.text(""));
        lines.add(PdfUtil.Line.text("Código: " + t.code()));
        lines.add(PdfUtil.Line.text("Data: " + (t.createdAt() == null ? "" : t.createdAt().format(TS))));
        lines.add(PdfUtil.Line.text("Conta: " + t.accountNumber()));
        if (t.counterpartyAccountNumber() != null) {
            lines.add(PdfUtil.Line.text("Conta relacionada: " + t.counterpartyAccountNumber()
                    + (t.counterpartyName() == null ? "" : " — " + t.counterpartyName())));
        }
        lines.add(PdfUtil.Line.text("Valor: " + Money.format(t.amount())));
        lines.add(PdfUtil.Line.text("Saldo anterior: " + Money.format(t.before())));
        lines.add(PdfUtil.Line.text("Saldo posterior: " + Money.format(t.after())));
        if (t.description() != null) lines.add(PdfUtil.Line.text("Descrição: " + t.description()));
        lines.add(PdfUtil.Line.text(""));
        lines.add(PdfUtil.Line.small("Responsável: " + (t.performedByName() == null ? "—" : t.performedByName())));
        return PdfUtil.document(lines);
    }

    private String shortType(String type) {
        return switch (type) {
            case "DEPOSITO" -> "DEPÓSITO";
            case "LEVANTAMENTO" -> "LEVANTAMENTO";
            case "TRANSFERENCIA_ENVIADA" -> "TRANSFERÊNCIA ENVIADA";
            case "TRANSFERENCIA_RECEBIDA" -> "TRANSFERÊNCIA RECEBIDA";
            case "JUROS" -> "JUROS";
            default -> type;
        };
    }
}
