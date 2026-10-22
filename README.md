# LEC Fantasy

Proyecto de fin de grado: juego fantasy de la LEC (League of Legends EMEA Championship) con
**web (Vue)** y **app Android (Kotlin)** que comparten la misma **API REST (Spring Boot)** y la
misma base de datos **MySQL**.

```
lec-fantasy/
├── backend/          API REST · Spring Boot 3.5 · Java 21 · MySQL 8 · Flyway
├── web/              (siguiente fase) Vue 3
├── android/          (siguiente fase) Kotlin
├── docs/             Modelo E-R actualizado (modelo-er.mmd / modelo-er.png)
└── docker-compose.yml  MySQL + backend en local
```

## Estado

| Parte | Estado |
|---|---|
| Base de datos (18 tablas, migraciones Flyway) | Hecho |
| API: registro, login, Google, recuperar contraseña | Hecho |
| API: ligas públicas/privadas, Liga Global, clasificaciones | Hecho |
| API: mercado, plantilla, alineación, mejoras | Hecho |
| API: jornadas, importación Leaguepedia / Oracle's Elixir, puntuación | Hecho |
| API: panel admin (usuarios, baneos, bloqueos, noticias, reportes, actividad) | Hecho |
| Web Vue | Pendiente |
| App Android | Pendiente |

---

## 1. Arrancar el backend en tu PC

### Opción A: con MySQL instalado (XAMPP, MySQL Workbench...)

1. Crea la base de datos y el usuario:
   ```sql
   CREATE DATABASE lecfantasy CHARACTER SET utf8mb4;
   CREATE USER 'lec'@'localhost' IDENTIFIED BY 'lec';
   GRANT ALL ON lecfantasy.* TO 'lec'@'localhost';
   ```
2. En `backend/` (PowerShell):
   ```powershell
   $env:ADMIN_EMAIL="admin@lecfantasy.local"; $env:ADMIN_PASSWORD="admin12345"
   .\gradlew.bat bootRun
   ```
   Flyway crea las tablas al arrancar. La API queda en `http://localhost:8080` y la
   documentación interactiva en `http://localhost:8080/swagger-ui.html`.

### Opción B: con Docker

```powershell
docker compose up --build
```

### Tests

```powershell
cd backend
.\gradlew.bat test
```

Los tests usan H2 en memoria (modo MySQL) con las mismas migraciones, así que no necesitan
MySQL. `FlujoCompletoTest` recorre el juego entero: registro, liga privada, fichajes,
alineación, mejora, importación del CSV, cierre de jornada, baneo, bloqueo de jugador,
login con Google, reportes, noticias y borrado de usuario.

> Gradle descarga solo el JDK 21 la primera vez si no lo encuentra (plugin *foojay*).

---

## 2. Desplegar en Railway

1. Sube el repositorio a GitHub.
2. En Railway: **New Project → Deploy from GitHub repo** y elige el repo.
   En el servicio, **Settings → Root Directory = `backend`** (usa el `Dockerfile`).
3. Añade una base de datos: **New → Database → MySQL**.
4. En el servicio del backend, **Variables**:

| Variable | Valor |
|---|---|
| `MYSQLHOST` | `${{MySQL.MYSQLHOST}}` |
| `MYSQLPORT` | `${{MySQL.MYSQLPORT}}` |
| `MYSQLDATABASE` | `${{MySQL.MYSQLDATABASE}}` |
| `MYSQLUSER` | `${{MySQL.MYSQLUSER}}` |
| `MYSQLPASSWORD` | `${{MySQL.MYSQLPASSWORD}}` |
| `JWT_SECRET` | una cadena aleatoria de 32+ caracteres |
| `ADMIN_EMAIL` / `ADMIN_PASSWORD` | el primer administrador |
| `CORS_ORIGINS` | URL de la web, p. ej. `https://lec-fantasy.up.railway.app` |
| `FRONTEND_URL` | la misma URL (para el enlace de recuperar contraseña) |
| `GOOGLE_CLIENT_IDS` | client IDs de Google (web y Android) separados por comas |
| `SPRING_MAIL_HOST`, `SPRING_MAIL_PORT`, `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD` | opcional, SMTP para correos |

5. **Settings → Networking → Generate Domain**. Comprueba `https://<dominio>/actuator/health`.

Sin SMTP configurado, los correos (recuperar contraseña, aviso de baneo) se escriben en el log.

### Login con Google

En Google Cloud Console → *APIs & Services → Credentials*, crea un **OAuth client ID** de tipo
*Web application* (y otro *Android* cuando hagamos la app). Pon esos IDs en `GOOGLE_CLIENT_IDS`.
La web y la app obtienen un *ID token* de Google y lo envían a `POST /api/auth/google`.

---

## 3. Reglas del juego (implementadas)

- **Presupuesto**: cada liga define el suyo (Liga Global: 50 M). Todos los jugadores entran a 8 M.
- **Plantilla**: 7 jugadores, 5 titulares (uno por rol: TOP, JNG, MID, ADC, SUP) y 2 suplentes.
  Un fichaje va a su puesto si está libre; si no, al banquillo.
- **Mercado libre**: varios mánagers de la misma liga pueden tener al mismo jugador.
- **Mercado cerrado** mientras una jornada está `EN_CURSO` (ni fichajes, ni alineación, ni mejoras).
- **Solo puntúan los titulares.**
- **Mejoras**: se compran para un jugador de tu plantilla y se aplican a la próxima jornada
  programada. Si se cumple la condición y el jugador es titular, sus puntos se multiplican por
  `multiplicador_bono`; si no, se pierde lo pagado.
- **Precios**: al cerrar una jornada, cada jugador sube o baja hasta un 8 % según su media de
  puntos frente a una referencia de 25.
- **Liga Global**: todo usuario entra automáticamente al registrarse.

### Puntos por partida (`service/Puntuacion.java`)

| Acción | Puntos |
|---|---|
| Kill | +3 |
| Asistencia | +2 |
| Muerte | −1 |
| CS | +0,02 por CS (1 cada 50) |
| Visión | +0,05 por punto (1 cada 20) |
| Doble / triple / cuádruple / pentakill | +1 / +2 / +5 / +10 |
| First blood (kill o asistencia) | +2 |
| Victoria | +2 |
| MVP de la partida (más puntos base) | +3 |

Ejemplo: un ADC 6/2/8 con 300 CS, 30 de visión y victoria suma 41,5 puntos.

---

## 4. Datos reales de la LEC

El administrador gestiona cada jornada así:

1. **Crear jornada** (`PROGRAMADA`) con fechas y, opcionalmente, la página de Leaguepedia
   (p. ej. `LEC/2026 Season/Spring Season`).
2. **Iniciar** → `EN_CURSO` (cierra el mercado).
3. **Importar** las partidas jugadas en esas fechas:
   - **Leaguepedia** (botón, automático): kills, muertes, asistencias, CS, visión y victoria.
     No incluye multikills ni first blood.
   - **CSV de Oracle's Elixir** (subir archivo): estadísticas completas, multikills incluidas.
     Se descarga en oracleselixir.com → Tools → Downloads (archivo anual, todas las ligas;
     el importador se queda solo con la LEC).
   Los equipos y jugadores que no existan se crean solos. Reimportar no duplica partidas.
4. **Previsualizar** los puntos ("validar puntos").
5. **Finalizar** → puntúa a todos los equipos, resuelve mejoras, actualiza precios y reabre el mercado.

> Usa una sola fuente por jornada: Leaguepedia y Oracle's Elixir identifican las partidas de
> forma distinta y se contarían dos veces.

---

## 5. Resumen de la API

Todas las rutas empiezan por `/api`. Las marcadas con 🔒 necesitan `Authorization: Bearer <token>`;
las de `/api/admin` necesitan además rol ADMIN. Detalle completo en Swagger.

**Auth**: `POST /auth/registro`, `/auth/login`, `/auth/google`, `/auth/olvido-password`, `/auth/restablecer-password`

**Usuario 🔒**: `GET|PUT /usuarios/me`, `PUT /usuarios/me/password`, `GET /usuarios/{id}`, `POST /reportes`

**Público**: `GET /equipos-pro`, `/jugadores?posicion=&equipoId=&q=`, `/jugadores/{id}`, `/jornadas`,
`/jornadas/{id}/partidas`, `/partidas/{id}`, `/mejoras`, `/noticias`, `/noticias/{id}`, `/ligas/publicas`

**Ligas 🔒**: `GET /ligas/mias`, `POST /ligas`, `POST /ligas/unirse` (código), `GET /ligas/{id}`,
`POST /ligas/{id}/unirse`, `GET /ligas/{id}/clasificacion?jornadaId=`, `DELETE /ligas/{id}/abandonar`, `DELETE /ligas/{id}`

**Equipo 🔒**: `GET /equipos/{id}`, `POST /equipos/{id}/fichajes`, `DELETE /equipos/{id}/plantilla/{jugadorId}`,
`PUT /equipos/{id}/alineacion`, `GET /equipos/{id}/transacciones`, `GET /equipos/{id}/puntuaciones`,
`GET|POST /equipos/{id}/mejoras`, `DELETE /equipos/{id}/mejoras/{mejoraAplicadaId}`

**Admin 🔒**: `GET /admin/dashboard`, `/admin/actividad`, `/admin/usuarios?q=&estado=&pais=`,
`GET /admin/usuarios/{id}`, `POST|DELETE /admin/usuarios/{id}/baneo`, `PUT /admin/usuarios/{id}/rol`,
`DELETE /admin/usuarios/{id}`, `GET|POST /admin/jugadores`, `PUT /admin/jugadores/{id}`,
`POST|DELETE /admin/jugadores/{id}/bloqueo`, `POST|PUT /admin/equipos-pro`, `POST|PUT|DELETE /admin/jornadas`,
`POST /admin/jornadas/{id}/iniciar`, `/importar/leaguepedia`, `/importar/csv`, `GET /previsualizacion`,
`POST /finalizar`, `DELETE /admin/partidas/{id}`, `GET|POST|PUT /admin/mejoras`,
`GET|POST|PUT|DELETE /admin/noticias`, `GET /admin/reportes?estado=`, `POST /admin/reportes/{id}/resolver`

---

## 6. Cambios respecto al E-R original

Ver `docs/modelo-er.png` (los campos nuevos están marcados como NUEVO). Resumen:

- **Tablas nuevas**: `equipo_pro`, `puntuacion_jornada`, `noticia`, `reporte`, `baneo`,
  `registro_actividad`, `password_reset_token`.
- **usuario**: fecha de nacimiento, país, equipo favorito, Riot ID, noticias, Google, fechas de registro y acceso.
- **jugador_real / partida_real**: los equipos pasan de texto a clave foránea a `equipo_pro`;
  id externo para importar; bloqueo de jugadores.
- **mejora**: `condicion` + `umbral` para que el sistema pueda evaluar si se cumple.
- **estadistica_jugador_partida**: campeón, dobles, victoria y equipo con el que jugó.
- Tipos `int` → `BIGINT` en las claves y `puntos` en decimal (los puntos llevan decimales por el CS y la visión).
