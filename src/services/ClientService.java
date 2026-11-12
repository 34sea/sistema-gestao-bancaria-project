package services;

import exceptions.BusinessException;
import exceptions.ForbiddenException;
import models.Client;
import models.User;
import persistence.BankStore;
import repositories.AuditRepository;
import repositories.ClientRepository;
import repositories.NotificationRepository;
import repositories.UserRepository;
import utils.Passwords;
import utils.Validation;

import java.time.LocalDate;
import java.util.List;

/** Gestão de clientes (registo, edição, estados) com criação de credencial de acesso. */
public class ClientService {

    private final ClientRepository clients = new ClientRepository();
    private final UserRepository users = new UserRepository();
    private final AuditRepository audits = new AuditRepository();
    private final NotificationRepository notifications = new NotificationRepository();

    /** Registo completo: dados pessoais + credencial de acesso (mesma operação de escrita). */
    public long register(User actor, String fullName, String documentNumber,
                         LocalDate birthDate, String gender, String address, String city,
                         String phone, String email, String username, String password) throws Exception {
        validatePersonal(fullName, documentNumber, email, phone, birthDate, username, password);
        if (username == null || username.isBlank()) {
            throw new BusinessException("Defina o utilizador de acesso do cliente.");
        }
        ensureUnique(null, fullName, documentNumber, email, username);

        String hash = Passwords.hash(password);
        String normPhone = Validation.requirePhone(phone);
        String normEmail = Validation.normalizeEmail(email);
        String name = Validation.requireName(fullName, "O nome completo");
        String doc = Validation.document(documentNumber);
        String user = Validation.username(username);
        return BankStore.writeOperation(v -> {
            Client p = new Client();
            p.fullName = name;
            p.documentNumber = doc;
            p.birthDate = birthDate;
            p.gender = gender;
            p.address = Validation.optionalText(address, "O endereço", Validation.SHORT_TEXT_MAX);
            p.city = Validation.optionalText(city, "A cidade", Validation.SHORT_TEXT_MAX);
            p.phone = normPhone;
            p.email = normEmail;
            long clientId = clients.insert(p);
            users.create(user, name, normEmail,
                    hash, "CLIENTE", clientId, null, null);
            audits.record(actor == null ? 0 : actor.id(), actor == null ? "SISTEMA" : actor.username(),
                    actor == null ? "ADMIN" : actor.role(), "REGISTAR_CLIENTE",
                    "CLIENTE", String.valueOf(clientId), "CONCLUIDA", documentNumber);
            if (actor != null) {
                notifications.createForClient(clientId, "Bem-vindo ao Banco",
                        "A sua conta de acesso foi criada com sucesso. Bem-vindo!", "GERAL");
            }
            return clientId;
        });
    }

    public void update(User actor, long clientId, String fullName, String documentNumber,
                       LocalDate birthDate, String gender, String address, String city,
                       String phone, String email) throws Exception {
        requireStaff(actor);
        validatePersonal(fullName, documentNumber, email, phone, birthDate, null, null);
        ensureUnique(clientId, fullName, documentNumber, email, null);
        String normPhone = Validation.requirePhone(phone);
        String normEmail = Validation.normalizeEmail(email);
        String name = Validation.requireName(fullName, "O nome completo");
        String doc = Validation.document(documentNumber);
        BankStore.writeOperation(v -> {
            Client p = clients.findById(clientId);
            if (p == null) throw new BusinessException("Cliente não encontrado.");
            p.fullName = name;
            p.documentNumber = doc;
            p.birthDate = birthDate;
            p.gender = gender;
            p.address = Validation.optionalText(address, "O endereço", Validation.SHORT_TEXT_MAX);
            p.city = Validation.optionalText(city, "A cidade", Validation.SHORT_TEXT_MAX);
            p.phone = normPhone;
            p.email = normEmail;
            clients.update(p);
            audits.record(actor.id(), actor.username(), actor.role(), "ACTUALIZAR_CLIENTE",
                    "CLIENTE", String.valueOf(clientId), "CONCLUIDA", null);
            return null;
        });
    }

    public void setStatus(User actor, long clientId, String status) throws Exception {
        requireStaff(actor);
        if (!List.of("ACTIVO", "INACTIVO", "BLOQUEADO").contains(status)) {
            throw new BusinessException("Estado de cliente inválido.");
        }
        BankStore.writeOperation(v -> {
            Client c = clients.findById(clientId);
            if (c == null) throw new BusinessException("Cliente não encontrado.");
            clients.setStatus(clientId, status);
            audits.record(actor.id(), actor.username(), actor.role(), "ESTADO_CLIENTE",
                    "CLIENTE", String.valueOf(clientId), "CONCLUIDA", "Novo estado: " + status);
            Long uid = notifications.credentialOf(clientId);
            if (uid != null) {
                if (!"ACTIVO".equals(status)) {
                    // sessões activas: num sistema local encerram-se simplesmente ao fechar a aplicação
                    notifications.create(uid, "Conta de acesso condicionada",
                            "O seu acesso foi " + ("BLOQUEADO".equals(status) ? "bloqueado" : "desactivado") + ".", "BLOQUEIO");
                }
            }
            return null;
        });
    }

    /** Actualização autónoma permitida ao cliente (dados de contacto). */
    public void updateOwn(User actor, String phone, String email, String address, String city) throws Exception {
        if (actor == null || !actor.isClient()) throw new BusinessException("Contexto de cliente inválido.");
        String normPhone = Validation.normalizePhone(phone); // opcional, mas formato obrigatório quando preenchido
        String normEmail = Validation.normalizeEmail(email);
        String addr = Validation.optionalText(address, "O endereço", Validation.SHORT_TEXT_MAX);
        String cty = Validation.optionalText(city, "A cidade", Validation.SHORT_TEXT_MAX);
        BankStore.writeOperation(v -> {
            Client p = clients.findById(actor.clientIdOrZero());
            if (p == null) throw new BusinessException("Cliente não encontrado.");
            p.phone = normPhone;
            p.email = normEmail;
            p.address = addr;
            p.city = cty;
            clients.update(p);
            return null;
        });
    }

    public List<Client> search(String query) { return clients.search(query); }

    public Client byId(long id) { return clients.findById(id); }

    // -------------------------------------------------------------- validações

    private void validatePersonal(String fullName, String documentNumber, String email, String phone,
                                  LocalDate birthDate, String username, String password) throws Exception {
        if (fullName == null || fullName.isBlank()) throw new BusinessException("O nome completo é obrigatório.");
        Validation.document(documentNumber);
        Validation.birthDate(birthDate);
        Validation.normalizeEmail(email);   // null quando vazio; erro quando formato inválido
        Validation.requirePhone(phone);     // obrigatório; 9 dígitos, prefixo 82–88 (+258 aceito)
        if (password != null) Validation.checkPassword(password);
        if (username != null) Validation.username(username);
    }

    /** Unicidade de BI/e-mail/utilizador com mensagens amigáveis (ignoreId permite a edição do próprio registo). */
    private void ensureUnique(Long ignoreId, String fullName, String documentNumber, String email, String username) {
        if (documentNumber != null && !documentNumber.isBlank()) {
            Client byDoc = clients.findByDocument(documentNumber);
            if (byDoc != null && (ignoreId == null || byDoc.id != ignoreId)) {
                throw new BusinessException("Já existe um cliente com este número de identificação.");
            }
        }
        if (email != null && !email.isBlank()) {
            Client byEmail = clients.findByEmail(email);
            if (byEmail != null && (ignoreId == null || byEmail.id != ignoreId)) {
                throw new BusinessException("Já existe um cliente com este e-mail.");
            }
        }
        if (username != null && !username.isBlank() && users.existsUsername(username)) {
            throw new BusinessException("Já existe um utilizador com este nome de acesso.");
        }
    }

    private void requireStaff(User actor) {
        if (actor == null || !actor.hasStaffRights()) {
            throw new ForbiddenException("Operação restrita a funcionários.");
        }
    }
}
