package controllers;

import models.Transaction;
import models.User;
import services.QueryService;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Adaptador das consultas (movimentos, extractos, estatísticas e relatórios). */
public class QueryController {

    private final QueryService service = new QueryService();

    public QueryService.MovementsPage movementsForClient(User actor, String type, String status,
                                                         String accountNumber, LocalDate from, LocalDate to,
                                                         int page, int pageSize) throws Exception {
        return service.movementsForClient(actor, type, status, accountNumber, from, to, page, pageSize);
    }

    public QueryService.MovementsPage movementsForAccount(User actor, String accountNumber, String type, String status,
                                                          LocalDate from, LocalDate to, int page, int pageSize) throws Exception {
        return service.movementsForAccount(actor, accountNumber, type, status, from, to, page, pageSize);
    }

    public QueryService.MovementsPage allMovements(String type, String status, String accountNumber,
                                                   LocalDate from, LocalDate to, int page, int pageSize) throws Exception {
        return service.allMovements(type, status, accountNumber, from, to, page, pageSize);
    }

    public Transaction byCode(User actor, String code) throws Exception {
        return service.byCode(actor, code);
    }

    public List<QueryService.TransferLine> transfersFor(User actor) throws Exception {
        return service.transfersFor(actor);
    }

    public QueryService.StatementData statement(User actor, String accountNumber, LocalDate from, LocalDate to) throws Exception {
        return service.statement(actor, accountNumber, from, to);
    }

    public Map<String, String> clientMovementStats(User actor) throws Exception {
        return service.clientMovementStats(actor);
    }

    public Map<String, String> employeeDashboardStats(User actor) throws Exception {
        return service.employeeDashboardStats(actor);
    }

    public Map<String, String> dashboardStats() throws Exception {
        return service.dashboardStats();
    }

    public Map<String, List<String[]>> dailySeriesByType() throws Exception {
        return service.dailySeriesByType();
    }

    public List<String[]> dailyVolume() throws Exception {
        return service.dailyVolume();
    }

    public List<String[]> accountTypeDistribution() throws Exception {
        return service.accountTypeDistribution();
    }
}
