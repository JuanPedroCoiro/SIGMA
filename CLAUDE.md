# SIGMA: instrucciones para Claude Code

CMMS (sistema de gestión de mantenimiento) para CIEMSA. Proyecto final de Universidad ORT (AP-ATI), equipo de dos: Juan Pedro Coiro y Rafael Ferrando.

El anteproyecto entregado es la fuente de verdad: nombres del dominio (Fig. 7), estados de la OT (Fig. 18), módulos (Fig. 19) y convenciones (1.13.1.6.1). Si algo choca con el anteproyecto o con este archivo, preguntar antes de seguir.

## Stack

- **Backend:** Java 25, Spring Boot 4, Spring Data JPA (Hibernate), Spring Security, Flyway, PostgreSQL 18. Paquete base `com.ciemsa.sigma`.
- **Frontend:** React 19, TypeScript, Vite, React Router, axios, Leaflet.
- **Tests:** JUnit + Testcontainers (`postgres:18`).
- **Entorno:** Windows + PowerShell, Docker Desktop. CI en GitHub Actions (Ubuntu).

## Comandos

En PowerShell (en el CI se usan los equivalentes de Linux):

- Base de datos: `docker compose -f docker/docker-compose.yml up -d`
- Backend: `cd backend; .\mvnw.cmd spring-boot:run`
- Tests y build del backend: `.\mvnw.cmd verify` (necesita Docker abierto por Testcontainers)
- Frontend: `cd frontend; npm ci; npm run dev`
- Lint y build del frontend: `npm run lint` y `npm run build`

Antes de dar una tarea por terminada, correr `mvnw verify` si se tocó el backend, y `npm run lint` + `npm run build` si se tocó el frontend.

## Arquitectura del backend

Monolito modular, con un paquete por módulo bajo `com.ciemsa.sigma`:

- `activos`: Instalacion, Equipo
- `ot`: Incidente, OrdenTrabajo, Reprogramacion, Adjunto, Organismo, CategoriaFalla, ReglaSLA
- `planificacion`
- `inventario`: Material, MovimientoStock
- `pendientes`
- `auditoria`: RegistroAuditoria
- `administracion`: Usuario, Cuadrilla, importación
- `shared`: seguridad, archivos, tareas, errores, BaseEntity

Estructura según el módulo (completa donde hay reglas, mínima donde hay altas y consultas):

- `ot`: cuatro capas `api` / `application` / `domain` / `infrastructure`, con los repositorios Spring Data en `infrastructure`.
- Módulos livianos (`activos`, `inventario`, `auditoria`, `administracion`): controller + servicio + entidad JPA + repositorio + DTO (records). Sin `infrastructure`.
- Solo consultas (`pendientes`, `planificacion`): servicio + controller con consultas de lectura que devuelven DTO.
- Fuera de `ot`, `infrastructure` solo para adaptadores reales (`AlmacenArchivos`, `LectorPlanilla`).
- Un solo modelo: las entidades JPA son el dominio.
- Entre módulos: llamar al servicio del otro módulo o publicar eventos de Spring en la misma transacción. Referenciar por id al escribir; joins al leer.
- Los paquetes se crean cuando una historia los necesita, no antes.

Reglas del núcleo:

- `EstadoOT` es un enum con comportamiento (patrón State): `PENDIENTE`, `ASIGNADA`, `EN_PROCESO`, `CERRADA_TECNICA`, `VALIDADA`, `ANULADA`. Una transición inválida lanza `TransicionInvalidaException`. El vencimiento del SLA es una condición derivada, no un estado.
- El vencimiento se calcula con `ReglaSLA.calcularVencimiento`: las reglas son datos. No hardcodear organismos (por ejemplo, MTOP).
- Inyectar `Clock` en lugar de llamar a `LocalDateTime.now()`. Usar las interfaces `UsuarioActual`, `AlmacenArchivos` y `LectorPlanilla`.
- El cierre de una OT es idempotente.
- La lógica de negocio va en servicios y entidades, nunca en los controllers.

## API

- REST bajo `/api/v1`, con recursos en plural y kebab-case (`/api/v1/ordenes-trabajo`). Las acciones son subrecursos con `POST` (`/api/v1/ordenes-trabajo/{id}/asignar`).
- Errores con `ProblemDetail`.
- Listados paginados con `?page&size&sort`, respuesta `PageResponse<T>`, 20 por defecto y máximo 100. Los catálogos chicos no se paginan.
- Seguridad: Spring Security con sesión por cookie + CSRF.

## Base de datos

- El esquema se maneja solo con Flyway, en `backend/src/main/resources/db/migration/V<n>__descripcion.sql`. Hibernate está en `ddl-auto=validate`: nunca crea ni modifica tablas.
- Una migración que ya está en `develop` no se edita: se agrega una nueva.
- Tablas en snake_case, singular y en español (`orden_trabajo`). PK `id_<entidad>`. FK con el nombre de la entidad referenciada (`id_instalacion`).
- Columna `version` para bloqueo optimista en las entidades que se editan de forma concurrente.
- Los maestros no se borran: se desactivan con `activo`.
- Los códigos visibles (`INC-nnnn`, `OT-nnnn`) se generan con secuencias.

## Estilo

- Java: Google Java Style (2 espacios). Los nombres del dominio van en español, como en el anteproyecto.
- TypeScript/React: ESLint + Prettier.
- Archivos en UTF-8 con finales LF. Nunca crear archivos con `echo ... >` en PowerShell: guarda en UTF-16.

## Git

- Ramas: `main` (línea base de cada iteración), `develop` (integración), `feature/HU-xx-descripcion` para historias y `<tipo>/<issue>-descripcion` para el resto, con el mismo tipo que el commit (`fix/1-esqueleto`, `chore/3-claude-md`). Siempre se crean desde `develop` y el PR va hacia `develop`.
- Commits y títulos de PR con Conventional Commits, usando la HU o el área como ámbito y el issue al final: `feat(HU-01): implementar login (#12)`, `fix(docker): corregir volumen (#3)`.
- Merge: las tareas entran a `develop` con squash (un commit por PR). El pase de `develop` a `main` al cierre de cada iteración es con merge commit, nunca squash, y se marca con una etiqueta `v0.x`.
- No hacer commit, push ni merge si no lo pide quien está trabajando.

## Qué evitar

- Agregar dependencias sin consultarlo antes.
- `ddl-auto=update` o `create`, y SQL fuera de las migraciones.
- Credenciales en el código: van en `docker/.env`, que no se sube al repo.
- Datos reales de CIEMSA en el repo (planillas de instalaciones, IPs, ubicaciones, usuarios): el repositorio es público. Solo datos de prueba.
- Keycloak u otro proveedor de identidad externo (descartado en el anteproyecto).
