package controllers;

import models.User;
import services.EmployeeService;
import services.QueryService;

import java.util.List;
import java.util.Map;

/** Adaptador de gestão de funcionários/utilizadores. */
public class EmployeeController {

    private final EmployeeService service = new EmployeeService();

    public long create(User actor, String username, String fullName, String email,
                       String password, String position, String department) throws Exception {
        return service.create(actor, username, fullName, email, password, position, department);
    }

    public long createAdmin(User actor, String username, String fullName, String email, String password) throws Exception {
        return service.createAdmin(actor, username, fullName, email, password);
    }

    public void setActive(User actor, long userId, boolean active) throws Exception {
        service.setActive(actor, userId, active);
    }

    public void resetPassword(User actor, long userId, String newPassword) throws Exception {
        service.resetPassword(actor, userId, newPassword);
    }

    public List<String[]> list(String search) throws Exception {
        return service.list(search);
    }
}
