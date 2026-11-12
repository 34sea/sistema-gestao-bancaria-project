# Sistema de Gestão Bancária — Java Swing (local, sem Docker)

Aplicação **desktop (Java Swing)** de simulação de gestão bancária académica, com
funcionários (balcão), administrador e portal do cliente. Reescrita da versão que
dependia de **Docker + MySQL 8.4 + noVNC**: agora corre com **apenas o JDK
instalado**, guarda os dados em **ficheiros CSV locais** (pasta `data/`) e **não tem
nenhuma dependência externa** (sem Maven, sem Gradle, sem base de dados, sem Docker).

> Simulação académica — não representa valores reais nem instituições financeiras.

---

## 1. Requisitos

| Requisito | Detalhe |
|---|---|
| **JDK 21** (recomendado) | Testado com OpenJDK Corretto 21. Funciona igualmente em JDK 17+. |
| Sistema operativo | Windows, Linux ou macOS (não precisa de mais nada) |
| **Não é necessário** | Docker, Docker Compose, MySQL, Maven, Gradle, internet |

Verifique o Java:

```bash
javac -version   # deve mostrar versão 21 (ou >= 17)
java -version
```

---

## 2. Estrutura do projeto

```text
src/
├── Main.java                 ← ponto de entrada (pacote por defeito)
├── exceptions/               ← erros de negócio (BusinessException, ForbiddenException)
├── models/                   ← entidades: User, Client, Account, Transaction, Notification, AuditLog, AccountType
├── persistence/              ← armazenamento local: BankStore + CsvCodec (ficheiros CSV em data/)
├── repositories/             ← camada de acesso a dados (contas, clientes, utilizadores, movimentos…)
├── services/                 ← regras de negócio: BankingService, AccountService, ClientService,
│                               EmployeeService, QueryService, StatementPdfService, AuthService,
│                               DemoDataSeeder
├── utils/                    ← Money, Ids, Passwords (PBKDF2), PdfUtil (gerador de PDF, sem libs)
├── controllers/              ← adaptadores finos entre vistas e serviços
└── views/                    ← telas Swing: LoginFrame, DashboardFrame (+ painéis do balcão),
                                ClientPortalFrame (portal do cliente), Theme, BarChartPanel
data/                         ← criado na 1ª execução: aqui ficam os dados (CSV)
out/                          ← criado na compilação (ficheiros .class)
compile.bat / run.bat         ← Windows (CMD/PowerShell)
compile.sh  / run.sh          ← Linux / macOS
```

A arquitectura segue **MVC/DAO**: as telas (`views`) só falam com `controllers`;
`services` contém todas as regras de negócio; `repositories` + `persistence`
guardam e leem tudo de ficheiros CSV.

---

## 3. Compilar

### Windows (CMD / PowerShell)
```bash
compile.bat
```
ou, manualmente, na raiz do projeto:
javac -encoding UTF-8 -d out src\Main.java src\exceptions\*.java src\models\*.java src\persistence\*.java src\repositories\*.java src\services\*.java src\utils\*.java src\controllers\*.java src\views\*.java
```

### Linux / macOS
```bash
./compile.sh      # ou: bash compile.sh
```

> O `-encoding UTF-8` é obrigatório (o código contém texto em português acentuado).

---

## 4. Executar

### Windows
```bash
run.bat
```
ou:
```bash
java -cp out Main
```

### Linux / macOS
```bash
./run.sh
```
ou:
```bash
java -cp out Main
```

A primeira execução cria dentro do projecto a pasta `data/` e semeia os dados
de demonstração (idempotente — só acontece uma vez).

---

## 5. Como os dados são armazenados

A persistência anterior (MySQL 8.4 em container Docker + migrations SQL) foi
substituída por um **armazenamento de ficheiros CSV embutido**:

- Os ficheiros vivem na pasta **`data/`**, na raiz do projeto:
  - `data/users.csv` — credenciais de acesso (utilizador, e-mail, senha PBKDF2, perfil, active…)
  - `data/clients.csv` — clientes (BI, género, cidade, telefone, e-mail, estado…)
  - `data/accounts.csv` — contas (número determinístico `1 + 8 dígitos`, saldo, estado, juros acumulados…)
  - `data/transactions.csv` — todos os movimentos (depósitos, levantamentos, transferências, juros)
  - `data/transfers.csv` — transferências (inclui `idempotency_key`)
  - `data/notifications.csv` — notificações do portal do cliente
  - `data/audit.csv` — registo de auditoria (append-only)

- Todo o estado vive em memória durante a execução e é **gravado em disco de forma
  sincronizada depois de cada operação** (escrita atómica: ficheiro temporário + move),
  garantindo consistência e que os dados sobrevivem ao fecho da aplicação.
- Unitariamente, cada operação financeira (depósito, levantamento, transferência,
  juros, registo…) corre dentro de uma **única operação de escrita sincronizada**
  — o equivalente local às transacções SQL da versão original.
- Para **recomeçar do zero**: pare a aplicação e apague a pasta `data/`
  (na próxima execução a semeadura de demonstração volta a correr).
- Para guardar os dados noutro local (ex.: testes): arranque com
  `-Dbanco.data.dir=C:\caminho\alternativo`.

As palavras-passe continuam protegidas com **PBKDF2 (210 000 iterações)**, tal
como na versão original — apenas JDK, sem bibliotecas.

---

## 6. Contas de demonstração (criadas na 1ª execução)

| Perfil | Utilizador | Palavra-passe | Login alternativo |
|---|---|---|---|
| Administrador | `admin` | `1234567890123` | — |
| Funcionário (caixa) | `func.caixa` | `Funcionario123!` | `func.gestor` / `Funcionario123!` |
| Cliente | `joao.silva` | `Cliente123!` | também `maria.chissano`, `carlos.mabjaia`, `ana.sitoe`, `rui.ngoenha` |

O cliente também consegue entrar com o **número de BI** ou o **e-mail**.

> É possível definir a senha do administrador inicial via variável de ambiente
> `APP_ADMIN_PASSWORD` (mínimo 12 caracteres) **antes** da primeira execução.

Dados iniciais: 5 clientes com conta corrente (+2 poupanças), depósitos e
levantamentos de demonstração e 3 transferências entre clientes.

---

## 7. Funcionalidades (mantidas da versão original)

### Balcão — administrador (`admin`)
- **Painel**: KPIs (clientes, funcionários, contas activas/bloqueadas/encerradas,
  saldo total, volume de depósitos/levantamentos, transferências, transacções)
  + 4 gráficos de barras (depósitos, levantamentos, transferências — últimos 14 dias,
  e volume diário) + distribuição de contas por tipo.
- **Clientes**: registar (com criação de credencial do portal), pesquisar, editar,
  mudar estado (ACTIVO / BLOQUEADO / INACTIVO).
- **Funcionários**: criar funcionário (cargo/departamento), activar/desactivar,
  repor palavra-passe.
- **Utilizadores**: criar utilizadores ADMIN adicionais, activar/desactivar,
  ver todas as credenciais do sistema.
- **Contas**: abrir conta (cliente + tipo CORRENTE/POUPANCA), pesquisar/filtrar,
  bloquear/reactivar, **encerrar (exclusivo do admin, exige saldo zero)** e
  **aplicar juros às contas poupança (5% do saldo, admin)**.
- **Depósitos / Levantamentos**: escolher conta, valor (aceita `1.250,50` e `1250.50`),
  descrição, diálogo de confirmação, comprovativo na hora com **“Guardar PDF”**.
- **Transferências**: origem/destino, **verificação do beneficiário** antes de
  transferir, modal de confirmação, comprovativo PDF.
- **Transacções**: filtros por tipo, estado, conta, intervalo de datas + pesquisa
  livre, **paginação**, comprovativo PDF de qualquer movimento.
- **Extractos**: extracto por conta e período com saldo inicial/final,
  pré-visualização em aplicação e gravação em **PDF**.
- **Relatórios**: os mesmos gráficos das séries diárias.
- **Auditoria**: últimos 300 registos, filtráveis por acção (login, depósitos,
  transferências, estados, juros…).
- **Tipos de conta**: tabela das taxas e operações permitidas.

### Balcão — funcionário (`func.caixa`)
O mesmo que o administrador, **excepto**: encerramento de contas, aplicação de
juros, gestão de funcionários/utilizadores, auditoria e relatórios; o Painel
mostra apenas a **actividade própria** (depósitos/levantamentos/transacções
realizados por si).

### Portal do cliente (Swing, abas)
- **As minhas contas**: saldo total + cartões de estatísticas (entradas, saídas,
  movimentos, transferências) + tabela das contas.
- **Transferir**: só a partir das **contas próprias** (regra de isolamento),
  verificação do beneficiário, confirmação, comprovativo PDF.
- **Movimentos**: filtros (tipo, estado, todas/uma conta, período), paginação e
  comprovativo PDF — o cliente só vê os movimentos das **suas** contas.
- **Extractos**: extracto por conta e período (pré-visualização + PDF).
- **Notificações**: lista com marcas NOVA/lida, marcar uma ou todas como lidas
  (indicador de não lidas no separador).
- **O meu perfil**: actualizar telefone/e-mail/endereço/cidade e alterar a
  própria palavra-passe (exige a actual + senha forte com confirmação).

### Segurança e regras de negócio (idênticas ao original)
- Login por **utilizador, e-mail ou BI**, senha com PBKDF2; contas inactivas
  (funcionário desactivado ou cliente bloqueado/inactivo) não entram.
- Valores > 0 com no máximo 2 casas decimais; sem saldo negativo (sem descoberto).
- Operações apenas em contas `ACTIVA` e de clientes `ACTIVO`.
- Transferência: origem ≠ destino, duas linhas de movimento (enviada/recebida)
  atomicamente, **idempotência** por chave (reenvio idêntico não duplica; chave
  reutilizada com dados diferentes é recusada).
- Isolamento de privacidade: cliente nunca vê movimentos/linhas de contas de
  terceiros; funcionários consultam tudo, clientes só o que é deles.
- Encerramento de conta exige **saldo zero** e é exclusivo do administrador;
  bloqueio de conta e de cliente revogam o acesso no momento.
- Tudo é registado em **auditoria** (sucessos e falhas).

### Validações de dados (frontend + backend)
Todas as validações existem nas **duas camadas**: a tela impede a submissão com
mensagens específicas (e destaca o campo inválido a vermelho) e os serviços
revalidam antes de gravar, garantindo a integridade mesmo se a tela for ignorada
(`utils/Validation.java` é a fonte única das regras).

| Campo | Regra |
|---|---|
| **Palavra-passe** | mínimo 8 caracteres + pelo menos uma maiúscula, uma minúscula, um número e um carácter especial; confirmação obrigatória e igual nos formulários de criação, reposição e alteração; mensagens indicam **exactamente** os requisitos em falta |
| **Celular (Moçambique)** | 9 dígitos, prefixo **82–88**; aceita `+258`/`258` e espaços/traços (ex.: `+258 84 111 0001` → `841110001`, gravado normalizado); recusa letras, símbolos, comprimentos e prefixos inválidos |
| **E-mail** | formato `nome@dominio.com` (opcional nos formulários); gravado em minúsculas |
| **BI** | 8–20 caracteres alfanuméricos (travessões permitidos), único |
| **Nome completo** | obrigatório, 3–120 caracteres, apenas letras e separadores |
| **Utilizador** | 4–30 caracteres (letras, números, `.`, `-`, `_`), único |
| **Valores monetários** | > 0, máx. 2 casas decimais, tecto de 10 mil milhões MZN (aceita `1.500,50` e `1500.50`) |
| **Datas** | componente de calendário (`DatePicker`) em todos os campos — nascimento não pode ser no futuro nem antes de 1900 |
| **Texto livre** | endereço, cidade, cargo, departamento: máx. 120/60 caracteres |
| **Campos obrigatórios** | marcados com `*`; o formulário lista todos os problemas de uma vez e não submete enquanto existirem erros |

---

## 8. Resolução de problemas

- **`javac` “illegal character”** → faltou `-encoding UTF-8` na linha de comando.
- **“Não foi possível inicializar os dados locais”** → confirme que a aplicação
  tem permissão de escrita na pasta do projecto (cria `data/`).
- **Quero apagar tudo e recomeçar** → feche a aplicação e apague a pasta `data/`.
- **Ecrã pequeno demais** → a janela abre maximizada; funciona também a janela normal.
- **Guardo os dados noutro disco/partição** → `java -Dbanco.data.dir=X:\caminho -cp out Main`.
