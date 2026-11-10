package controllers;

import models.User;
import services.BankingService;

import java.math.BigDecimal;

/** Adaptador das operações financeiras (depósito, levantamento, transferência). */
public class BankingController {

    private final BankingService service = new BankingService();

    public BankingService.OperationResult deposit(User actor, String accountNumber, BigDecimal amount, String note) throws Exception {
        return service.deposit(actor, accountNumber, amount, note);
    }

    public BankingService.OperationResult withdraw(User actor, String accountNumber, BigDecimal amount, String note) throws Exception {
        return service.withdraw(actor, accountNumber, amount, note);
    }

    public BankingService.TransferResult transfer(User actor, String sourceNumber, String targetNumber,
                                                  BigDecimal amount, String description, String idempotencyKey) throws Exception {
        return service.transfer(actor, sourceNumber, targetNumber, amount, description, idempotencyKey);
    }

    public BankingService.Beneficiary previewBeneficiary(User actor, String accountNumber) {
        return service.previewBeneficiary(actor, accountNumber);
    }
}
