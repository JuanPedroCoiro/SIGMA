# SIGMA - CMMS para CIEMSA

Sistema de Gestión de Mantenimiento (CMMS) para CIEMSA.

## Tecnologías

- **Backend:** Java 25, Spring Boot 4, Flyway, PostgreSQL 18
- **Frontend:** React 19, TypeScript, Vite
- **Base de datos:** PostgreSQL 18 (Docker)
- **CI/CD:** GitHub Actions

## Requisitos previos

- Git
- Docker Desktop (incluye Docker Compose)
- Java 25 (Temurin)
- Node.js 24.x (ver `.nvmrc`) y npm 11.x

## Instalación y ejecución

Los comandos están escritos para PowerShell (Windows). En bash, `copy` es `cp` y `.\mvnw.cmd` es `./mvnw`.

### 1. Clonar el repositorio

```powershell
git clone git@github.com:JuanPedroCoiro/SIGMA.git
cd SIGMA
git switch develop
```

Sin clave SSH configurada, usar `https://github.com/JuanPedroCoiro/SIGMA.git`.

### 2. Variables de entorno

```powershell
copy docker\.env.example docker\.env
```

`docker/.env` no se sube a Git. Los valores de `.env.example` son solo para desarrollo local y coinciden con los valores por defecto de `backend/src/main/resources/application.properties`. Si se cambia el usuario o la clave en `.env`, hay que definir las mismas variables (`POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`) al correr el backend.

### 3. Levantar PostgreSQL

```powershell
docker compose -f docker/docker-compose.yml up -d
docker compose -f docker/docker-compose.yml ps
```

`sigma-db` tiene que aparecer en estado `Up`.

### 4. Backend

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

Al arrancar, Flyway aplica las migraciones de `src/main/resources/db/migration`. El backend queda en `http://localhost:8080`.

### 5. Frontend (en otra terminal)

```powershell
cd frontend
npm ci
npm run dev
```

`npm ci` instala exactamente las versiones de `package-lock.json`. Se corre la primera vez y cada vez que cambie el lock. El frontend queda en `http://localhost:5173`.

## Testing

### Backend

Requiere Docker abierto: los tests levantan un PostgreSQL temporal con Testcontainers.

```powershell
cd backend
.\mvnw.cmd verify
```

### Frontend

```powershell
cd frontend
npm run lint
npm run build
```

## Estructura del proyecto

```text
SIGMA/
├── .github/workflows/   # CI (GitHub Actions)
├── backend/             # Spring Boot + Flyway
├── frontend/            # React + Vite + TypeScript
├── docker/              # PostgreSQL 18 con Docker Compose
│   ├── docker-compose.yml
│   └── .env.example
├── .editorconfig        # Formato común para todos los editores
├── .gitattributes       # Finales de línea (LF)
├── .nvmrc               # Versión de Node.js (24)
└── README.md
```

## Ramas y flujo de trabajo

- `main`: versión estable. Recibe `develop` al cierre de cada iteración (línea base).
- `develop`: rama de integración. Todo entra por Pull Request.
- `feature/HU-xx-descripcion`: una rama por historia de usuario.
- `<tipo>/<issue>-descripcion`: el resto de las tareas, con el mismo tipo que el commit (`fix/1-esqueleto`, `chore/3-claude-md`, `ci/...`, `docs/...`, `build/...`).

Todas las ramas se crean desde `develop`, y cada tarea arranca con un issue en GitHub.

```powershell
git switch develop
git pull
git switch -c feature/HU-01-login-basico
```

Los commits siguen [Conventional Commits](https://www.conventionalcommits.org/es/): `tipo(ámbito): descripción (#issue)`. En las historias de usuario el ámbito es la HU; en el resto, la parte del proyecto que se toca:

```powershell
git add .
git commit -m "feat(HU-01): implementar login con Spring Security (#12)"
git push -u origin feature/HU-01-login-basico
```

Otros ejemplos:

```text
fix(docker): corregir volumen de PostgreSQL 18 (#3)
docs(readme): actualizar pasos de instalación (#3)
ci: agregar build del frontend (#5)
```

Después se abre un Pull Request **hacia `develop`**, con el título en el mismo formato que los commits, y lo aprueba el otro integrante antes del merge.

- **Tareas → `develop`:** se mergean con **Squash and merge**, así cada PR entra como un solo commit con el título del PR. Como los PR no van a la rama por defecto, el issue se cierra a mano después del merge.
- **Cierre de iteración (`develop` → `main`):** por Pull Request con **Create a merge commit**, nunca con squash, para que las dos ramas no se separen. Después se marca con una etiqueta (`v0.1`, `v0.2`, ...).

Tipos más usados: `feat`, `fix`, `docs`, `test`, `refactor`, `chore`, `ci`, `build`.

## Convenciones

- Archivos en UTF-8 con finales LF (lo aplican `.editorconfig` y `.gitattributes`). Crear y editar archivos desde el editor, no con `echo ... >` en PowerShell: Windows PowerShell guarda en UTF-16.
- Java: Google Java Style. TypeScript/React: ESLint + Prettier.
- El repositorio es público: nunca subir datos reales de CIEMSA (planillas de instalaciones, IPs, ubicaciones, usuarios) ni credenciales. Solo datos de prueba.
- Base de datos: el esquema se maneja solo con migraciones Flyway (`V<n>__descripcion.sql`). Una migración ya aplicada no se edita: se agrega una nueva. Tablas en snake_case singular, PK `id_<entidad>`.

## Problemas frecuentes

- **Puerto 5432 ocupado:** cambiar `5432:5432` por `5433:5432` en `docker/docker-compose.yml` y el puerto de `spring.datasource.url` en `application.properties` (sin commitear ese cambio).
- **`password authentication failed`:** PostgreSQL toma usuario y clave solo la primera vez que crea el volumen. Si se cambiaron en `.env` después, recrear la base (borra los datos locales): `docker compose -f docker/docker-compose.yml down -v` y volver a levantarla.
- **El contenedor `sigma-db` arranca y se cierra:** PostgreSQL 18 espera el volumen en `/var/lib/postgresql` (no en `/var/lib/postgresql/data`). Revisar con `docker compose -f docker/docker-compose.yml logs`.
- **`npm ci` falla con `ERESOLVE`:** hay un conflicto de versiones en `package.json`. No usar `--force` ni `--legacy-peer-deps`: corregir las versiones y regenerar `package-lock.json`.
- **Un archivo se ve con `NUL` o caracteres raros:** está guardado en UTF-16. Reabrirlo con esa codificación y guardarlo como UTF-8.

## Integrantes

- Juan Pedro Coiro
- Rafael Ferrando

## Licencia

Proyecto académico - Universidad ORT Uruguay.
