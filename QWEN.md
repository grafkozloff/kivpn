# QWEN.md — kivpn

## Обзор проекта

`kivpn` — бесплатный консольный VPN-клиент для Windows 10/11 x64 для частного использования (обход блокировок).
Приложение работает как менеджер конфигураций и оркестратор подключения: хранит и импортирует серверные профили,
проверяет их доступность, выбирает активный сервер и управляет внешним VPN-модулем, который устанавливает сетевое соединение.
Без внешнего модуля (или без прав администратора) приложение работает в режиме менеджера конфигураций.

**Текущая стадия: работающий клиент.** Реализованы: консольный цикл со всеми командами (`help`, `add`, `import`, `list`,
`check`, `use`, `connect`, `disconnect`, `status`, `module`, `source`, `update`, `export`, `exit`), SQLite-слой
(схема версии 2, включая таблицу `settings`), парсеры `vless://`/`ss://`/`trojan://`, провайдеры источников (файл, подписка),
проверка доступности, автовыбор сервера, оркестрация подключения и **полный туннель (TUN) через sing-box** (F38–F40),
экспорт профилей, `run.bat`. Сборка и 176 тестов проходят; дымовой запуск jar проверен.
Ещё нет: команд удаления/изменения профилей (F16), failover (F30), провайдеров VPN Gate и публичных списков (F22, F23),
опциональных протоколов (F37: wireguard/amneziawg/hysteria2/tuic). Живая проверка TUN на реальном сервере не выполнена —
у пользователя пока нет сервера (подготовка описана в README §§1–12).

Целевой стек: Java 21 (`maven.compiler.release=21`), Maven, **Spring Boot 3.5.16 (без web)**, SQLite (`org.xerial:sqlite-jdbc` 3.46.1.0),
`jackson-databind` (версия из parent; params профилей и конфиги модулей — JSON), консольный ввод/вывод.

## Структура репозитория

```
kivpn/
├── QWEN.md                  # этот файл — контекст для агента
├── README.md                # пользовательская документация: подготовка сервера (§§1–12), сборка/запуск/команды (§13)
├── requirements.md          # таблица требований с кодами — источник истины по поведению
├── pom.xml                  # Maven + Spring Boot 3.5: Java 21, sqlite-jdbc, jackson-databind, repackage → target/kivpn.jar
├── run.bat                  # ручной запуск: chcp 65001, cd /d %~dp0, java из KIVPN_JAVA > JAVA_HOME > PATH, проверки jar/java
├── .gitignore               # target/, data/
├── src/main/java/org/kivpn/
│   ├── Main.java            # @SpringBootApplication: запуск контекста, затем консольного цикла
│   ├── cli/
│   │   ├── ConsoleAdapter.java  # @Component: консольный цикл, UTF-8 потоки, приглашение, вывод результата/ошибок
│   │   └── CommandRouter.java   # @Component: маршрутизация всех команд, русские сообщения и справка
│   ├── db/
│   │   ├── SqliteConnectionManager.java  # соединения с PRAGMA (WAL, synchronous, foreign_keys, busy_timeout)
│   │   ├── SchemaInitializer.java        # идемпотентные миграции по PRAGMA user_version (1: базовые таблицы, 2: settings)
│   │   ├── ProfileRepository.java        # CRUD профилей, выбор активного
│   │   ├── SourceRepository.java         # CRUD источников, включение/отключение
│   │   ├── CheckRepository.java          # результаты проверок, последняя на профиль
│   │   └── SettingsRepository.java       # key/value настройки (module.path, module.type)
│   ├── model/               # записи Profile, Source, CheckResult
│   ├── parser/              # ProfileParserDispatcher + VlessParser, SsParser, TrojanParser, ParserSupport
│   ├── service/             # ProfileService (add/import/list/check/use/useAuto/export), ServerChecker, ServerSelector
│   ├── source/              # SourceService + SourceProviderDispatcher, FileSourceProvider, SubscriptionSourceProvider
│   └── vpn/
│       ├── ConnectionService.java   # оркестрация connect/disconnect/status/module; проверки sing-box/админ/wintun
│       ├── AdminCheck.java          # проверка прав администратора (net session, таймаут 5 с)
│       ├── ProcessManager.java      # дочерний процесс модуля, остановка при disconnect/смене профиля/выходе (@PreDestroy)
│       ├── ConfigGeneratorDispatcher.java + ConfigGenerator, ConfigSupport
│       ├── SingBoxConfigGenerator.java  # TUN-inbound (auto_route/strict_route/sniff, IPv4+IPv6), DNS-hijack, mixed 2080
│       └── XrayConfigGenerator.java     # локальные SOCKS 10808/HTTP 10809 (connect с xray запрещён: нет TUN на Windows)
├── src/main/resources/
│   ├── application.properties   # banner-mode=off, logging.level.root=WARN, kivpn.check.timeout-ms=3000, kivpn.source.timeout-ms=10000
│   └── logback-spring.xml       # журналы Spring в stderr, чтобы не засорять консольный интерфейс
├── src/test/java/org/kivpn/     # 176 тестов без сети: cli/, db/, parser/, service/, source/, vpn/ + ApplicationContextTest
│   └── ... vpn/FakeProcessManager.java, vpn/FakeAdminCheck.java   # фейки для тестов оркестрации
├── src/test/resources/junit-platform.properties  # автоочистка @TempDir отключена (Windows WAL-локи, см. ниже)
├── diagrams/
│   ├── container.puml       # C4-диаграмма контейнеров
│   ├── component.puml       # C4-диаграмма компонентов (Connection Service описывает TUN через sing-box)
│   ├── kivpn-c4-container.png   # рендер container.puml
│   └── kivpn-c4-component.png   # рендер component.puml
└── .idea/                   # конфигурация IntelliJ IDEA (не отслеживается git)
```

Рабочие артефакты: `target/kivpn.jar` (сборка), `data/kivpn.db` (SQLite), `data/module-config.json` и `data/module.log`
(конфиг и журнал внешнего модуля); каталоги `target/` и `data/` исключены из git.

## Ключевые файлы

- **`requirements.md`** — таблица требований с кодами: `F1–F42` (функциональные), `U1–U8` (удобство),
  `R1–R12` (надёжность), `P1–P6` (производительность), `S1–S10` (поддерживаемость), `+R1–+R15` (ограничения).
  Любое изменение поведения должно сверяться с этой таблицей; в описаниях изменений и коммитах принято ссылаться на коды требований.
- **`README.md`** — документация для пользователя: как получить бесплатный сервер (Oracle Cloud Always Free,
  Ubuntu/Debian, панель 3x-ui, VLESS+Reality / Shadowsocks-2022), какие порты открыть (22/tcp, 443/tcp, 443/udp),
  и §13: сборка, запуск, команды, требования полного туннеля (sing-box + wintun.dll + права администратора).
- **`diagrams/container.puml`** — C4-контейнеры: `run.bat`, консольное приложение (`target/kivpn.jar`),
  SQLite WAL (`data/kivpn.db`), файлы конфигурации VPN-модуля; внешние системы: VPN-модуль, источники серверов, VPN-сервер пользователя.
- **`diagrams/component.puml`** — C4-компоненты приложения: Main/Bootstrap, Console Adapter, Command Parser/Router,
  Profile Service, Profile Parser, Source Providers, Server Checker, Server Selector, Connection Service (TUN через sing-box),
  Config Generator, Process Manager, Repository, Schema Initializer/Migrations, SQLite Connection Manager.

Диаграммы и README должны оставаться синхронизированными с `requirements.md`; после правок `.puml` перерендеривать PNG в `diagrams/`.

## Сборка и запуск

### Приложение

- Сборка: `mvn clean package` → исполняемый `target/kivpn.jar` (F6, F7, S4). Проверено: сборка и 176 тестов проходят.
  Исполняемый jar собирает spring-boot-maven-plugin (repackage, зависимости в `BOOT-INF/`, запуск через `JarLauncher`).
- Прямой запуск: `java -jar target/kivpn.jar` — проверено, консольный цикл работает.
- Запуск через `run.bat` из корня проекта (F4, F5, R4, R5, U4): `chcp 65001`, переход в каталог скрипта,
  выбор java (`KIVPN_JAVA` > `JAVA_HOME` > PATH), проверки наличия jar и java, ошибки в stderr.
  Для `connect` (TUN) запускать **от имени администратора**; для режима менеджера конфигураций права не нужны.
- Данные: `data/kivpn.db` создаётся идемпотентно при первом запуске (F8, F9); путь настраивается свойством `kivpn.db.path`.
- **JDK 21 обязателен** (`maven.compiler.release=21`). Установлен Oracle JDK 21.0.12.1: `C:\Program Files\Java\jdk-21.0.12.1`.
  Пользовательский `JAVA_HOME` указывает на JDK 21, но машинный — на JDK 17, а `java` в PATH — Java 8;
  уже запущенные процессы (включая shell агента) наследуют старое окружение, поэтому сборку запускать как
  `set "JAVA_HOME=C:\Program Files\Java\jdk-21.0.12.1" && mvn -B clean package`.

### Рендеринг диаграмм (проверено)

PlantUML CLI (jar не хранится в репозитории; кешируется в `%TEMP%`; C4-библиотека подключается по URL, нужна сеть):

```
java -jar plantuml-1.2025.4.jar -charset UTF-8 -tpng -o <абсолютный путь>/diagrams diagrams/component.puml
```

- **Версия 1.2025.4**: PlantUML 1.2026.x вместе с C4-PlantUML master вжигает в PNG жёлтый баннер deprecated-переменных
  (`NEW_C4_STYLE`, `ROUNDED_STYLE`, `NO_LAY_ROTATE` без `$`) — они объявляются внутри библиотеки, правкой `.puml` не убрать.
- **Обязательно `-charset UTF-8`**: без него CLI на Windows читает файлы в локальной кодировке и кириллица превращается в mojibake.
  `-checkonly` проходит и при неверной кодировке — проверять именно рендер.
- **`-o` задавать абсолютным путём**: CLI разрешает `-o` относительно каталога исходного файла, поэтому `-o diagrams`
  из корня проекта пишет в `diagrams/diagrams/`.
- Имена PNG берутся из идентификаторов `@startuml` (`kivpn-c4-container`, `kivpn-c4-component`).
- В актуальной C4-PlantUML у `System_Ext`/`System` **нет параметра технологии**: сигнатура
  `($alias, $label, $descr, $sprite, ...)`. Технология внешних систем включается в текст описания;
  передача технологии третьим аргументом даёт артефакты `<$...>` в рендере.
  У `Container`, `ContainerDb`, `Component` параметр технологии есть (третий аргумент).

## Соглашения и правила разработки

Язык и интерфейс:
- Java 21, стандартный Maven-проект; зависимости фиксируются в `pom.xml` (S1–S3). Новые зависимости — только по согласованию
  (jackson-databind добавлен явным решением пользователя для JSON params и конфигов модулей).
- Spring Boot используется как каркас DI/конфигурации/упаковки, **без web-стека**: приложение остаётся консольным (F2, F3).
  Баннер отключён (`spring.main.banner-mode=off`), журналы Spring направлены в stderr (`logback-spring.xml`, уровень WARN),
  чтобы stdout принадлежал только консольному интерфейсу. Консольный цикл запускается из `main()` после поднятия контекста,
  поэтому `@SpringBootTest` не блокируется на чтении stdin.
- Только консольный интерфейс (`stdin`/`stdout`), без GUI, установщика, служб и автозапуска (F2, F3, +R4–+R6).
- Сообщения пользователю — на русском, ошибки — в `stderr` и в понятном виде (U2, U3, F14).
- Кодировка консоли UTF-8; в `run.bat` — `chcp 65001` (U4).
- Набор команд: `help`, `add`, `import`, `list`, `check`, `use <id|auto>`, `update`, `connect`, `disconnect`, `status`,
  `module <путь> [xray|sing-box]`, `source <add|list|enable|disable|remove>`, `export <id|all> [файл]`, `exit` (U7, F42).
  Команд удаления/изменения профилей пока нет (дыра F16). Секреты и ключи не показываются в списках и не попадают в журналы —
  только маскированные значения (U8, R10).

База данных:
- Только SQLite, файл `data/kivpn.db`; при открытии соединения применять
  `PRAGMA journal_mode=WAL; synchronous=NORMAL; foreign_keys=ON; busy_timeout=5000;` (F10, F11, P1, +R9).
  `SqliteConnectionManager` открывает соединение на операцию (все закрываются try-with-resources),
  `SchemaInitializer` применяет миграции по `PRAGMA user_version` в транзакции; удаление источника не удаляет
  профили (`ON DELETE SET NULL`), удаление профиля каскадно удаляет его проверки.
- Инициализация каталога `data`, файла БД и схемы — идемпотентная; повторный запуск не теряет данные (F9, R3, R11).
- Схема версионируется, изменения — через миграции или совместимое чтение (S8). Текущая версия — 2 (`settings`).
- Настройки модуля живут в `settings`: ключи `module.path`, `module.type` (задаются командой `module`).
- JDBC-ресурсы закрываются при завершении; обновление источников не удаляет пользовательские профили без явной команды (F13, R2, R9).

Профили и парсеры:
- `params` профиля — JSON-объект (Jackson): uuid/ключи плюс параметры ссылки (type, security, pbk, sid, sni, flow, …);
  исходная ссылка хранится в `uri` для экспорта (F42). Формат един для всех парсеров и генераторов конфигов.
- Парсеры и провайдеры — отдельные бины с диспетчерами (`ProfileParserDispatcher`, `SourceProviderDispatcher`);
  новый протокол или источник добавляется новым бином без переработки приложения (S6, S7).

Сеть, процессы и туннель:
- Все сетевые операции — с таймаутами (`kivpn.check.timeout-ms`, `kivpn.source.timeout-ms`); ошибки сети не роняют приложение (R6, R7, P5).
- Никаких фоновых автоматических процессов: обновление источников и проверки — только по команде пользователя (F24, P6).
- **Подключение — полный туннель (TUN) только через sing-box** (решение пользователя): xray не поддерживает TUN на Windows,
  `connect` с xray даёт понятную ошибку с предложением sing-box. Перед запуском `ConnectionService` проверяет:
  тип модуля sing-box, права администратора (`AdminCheck`, `net session`), наличие `wintun.dll` рядом с sing-box.exe.
- Конфиг sing-box: tun-inbound (IPv4+IPv6 адреса, `auto_route`, `strict_route`, `sniff`, stack system), DNS через туннель
  (DoH с detour proxy, outbound `dns-out` + правило перехвата DNS), `auto_detect_interface` против routing loop,
  приватные IP → direct, остальное → proxy; локальный mixed-прокси 2080 сохранён (F38–F40).
- Внешний VPN-модуль запускается как дочерний процесс (`<module> run -config data/module-config.json`, журнал `data/module.log`)
  и обязательно останавливается при отключении, смене профиля и выходе; «осиротевших» процессов не остаётся (F34, F35, R8, R12).
  Остановка принудительная: адаптер wintun исчезает вместе с процессом; при аварийном сбое маршруты теоретически могут остаться.
- Источники серверов — отдельные провайдеры (сейчас файл и подписка; VPN Gate и публичные списки ещё не реализованы),
  включаемые/отключаемые; публичные источники вспомогательные и могут быть недоступны (S6, F20–F23, +R13).
- Поддержка протоколов расширяемая: реализованы `vless`, `ss`, `trojan`; опционально `wireguard`, `amneziawg`, `hysteria2`, `tuic` (F37, S7).

Тестирование и документация:
- Код тестируется без реальной сети: парсеры, БД, логика выбора, оркестрация с фейками (`FakeProcessManager`, `FakeAdminCheck`) (S10).
- Автоочистка `@TempDir` отключена (`junit-platform.properties`): на Windows SQLite WAL-файлы во временном каталоге
  бывают заблокированы (антивирус/индексатор), и очистка падает с `DirectoryNotEmptyException`.
- Документация по сборке, запуску и командам поддерживается в `README.md` (S9); архитектурные изменения отражаются в `diagrams/`.
- Проект разрабатывается одним человеком для частного локального использования: предпочтительны простые прагматичные решения
  без командной инфраструктуры (CI-пайплайны, отдельные роли и т.п.) (+R7).

## Ограничения (не нарушать)

- Целевая ОС только Windows 10/11 x64; Java 21; Maven; SQLite (без внешних СУБД).
- Без GUI, установщиков (`.exe`, `.msi`, `jpackage` и т.п.), служб, автозапуска и планировщика.
- Без регистрации, аккаунтов и оплаты внутри приложения; собственного платного VPN-сервиса нет.
- Фактическое VPN-подключение допускается только через внешний исполняемый модуль; путь к нему задаёт пользователь.
- Для создания системного туннеля нужны права администратора (TUN через sing-box, +R14).

## Текущее состояние и ближайшие шаги

Выполнено: каркас Spring Boot; SQLite-слой с миграциями; парсеры и провайдеры источников; проверки и выбор сервера;
оркестрация подключения и экспорт; `run.bat`; полный туннель TUN через sing-box (F38–F40) с проверками админа и wintun.dll;
176 тестов; README §13 и C4-диаграммы синхронизированы.

Ближайшие шаги:
1. **Живая проверка TUN**: у пользователя пока нет сервера — подготовка по README §§1–12 (Oracle Cloud Always Free,
   3x-ui, VLESS+Reality), затем `add` → `module <путь> sing-box` → `check` → `use` → `connect` от имени администратора.
2. Команды удаления/изменения профилей (F16).
3. Failover — автопереключение на следующий рабочий сервер при недоступности активного (F30).
4. Провайдеры VPN Gate и публичных списков (F22, F23).
5. Опционально: дополнительные протоколы F37.
