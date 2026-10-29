# Ruta Entre Municipios — Incremento 1 (≈20 %)

Historias implementadas: **HU-01 Registro persistente · HU-02 Inicio de sesión real · HU-03 Mapa y origen · HU-07 Recuperación de contraseña** (12 SP de 54).
Extra mínimo: regla de destino de **HU-04 / BUG-01** (Buscar viaje deshabilitado si el destino está vacío o es igual al origen).

**Equipo:** Johan Hurtado · Alejandro Torres · Santiago Ordoñez · Diego Monroy

## Versiones

| Herramienta | Versión |
|---|---|
| Java (JDK) | **21** (LTS) — usa records, sealed classes, pattern matching en `switch`, hilos virtuales |
| Maven (opcional) | 3.9+ |
| Dependencias de producción | **Ninguna** (solo el JDK) |
| Pruebas | JUnit 5.10.2 |
| Frontend | HTML + CSS + JavaScript |
| Mapa | **Google Maps JavaScript API** (con clave) · respaldo automático: Leaflet 1.9.4 + OpenStreetMap |

## Ejecutar (sin modificar código)

**Opción A — sin Maven (solo JDK 21):**

```bash
# Linux / macOS
./run.sh
# Windows
run.bat
```

**Opción B — con Maven:**

```bash
mvn clean package          # compila y corre las 32 pruebas
java -jar target/ruta-entre-municipios.jar
```

Abrir **http://localhost:8080**. Los datos quedan en la carpeta `data/` (se crea sola).

## Datos de prueba

| Dato | Valor |
|---|---|
| Usuario precargado | `demo@rutamunicipios.co` / `Demo2026!` |
| Correo inválido | `johan@` → "Revisa el formato" |
| Contraseña límite | `1234567` (7) se rechaza, `12345678` (8) se acepta |
| Coordenadas Tuluá | 4.0847, −76.1954 |
| Bandeja de correos de prueba | http://localhost:8080/dev/correos |

## Google Maps (HU-03)

El mapa usa **Google Maps** cuando hay una clave configurada. Sin clave, sin internet o si Google rechaza la
clave, la app cambia sola a **Leaflet + OpenStreetMap**, así la demo nunca se cae.

1. Entra a https://console.cloud.google.com, crea un proyecto y activa una **cuenta de facturación**
   (Google la exige, pero tiene uso gratuito mensual suficiente para el proyecto).
2. En **APIs y servicios → Biblioteca** habilita **Maps JavaScript API** (y, opcional, **Geocoding API**).
3. En **APIs y servicios → Credenciales → Crear credenciales → Clave de API** copia la clave.
4. Recomendado: en la clave, **Restricciones de aplicación → Sitios web** y agrega `http://localhost:8080/*`;
   en **Restricciones de API** deja solo Maps JavaScript API.
5. En la carpeta del proyecto copia `config.properties.example` como **`config.properties`** y pega la clave:

   ```properties
   GOOGLE_MAPS_API_KEY=AIza...tu_clave
   GOOGLE_MAP_ID=DEMO_MAP_ID
   ```
6. Ejecuta `run.bat`. La consola muestra `Mapa: Google Maps` y en el mapa aparece la etiqueta **GOOGLE MAPS**.

Si el mapa muestra "Google Maps rechazó la clave", revisa que la Maps JavaScript API esté habilitada, que la
facturación esté activa y que `http://localhost:8080/*` esté en los sitios permitidos.

Para que el **servidor** también use Google al convertir coordenadas en municipio, pon en
`GOOGLE_GEOCODING_API_KEY` una clave con **Geocoding API** y sin restricción de sitio web (las claves
restringidas por sitio solo sirven en el navegador). Si no, el servidor usa OpenStreetMap.

## Configuración (variables de entorno o `config.properties`, todas opcionales)

| Variable | Defecto | Uso |
|---|---|---|
| `PORT` | 8080 | Puerto HTTP |
| `APP_BASE_URL` | http://localhost:8080 | Base del enlace que llega al correo |
| `DATA_DIR` | data | Carpeta de persistencia |
| `RESET_TOKEN_TTL_MINUTES` | 30 | Vencimiento del enlace (poner `1` para demostrar el vencimiento en vivo) |
| `SESSION_TTL_MINUTES` | 120 | Duración de la sesión |
| `GOOGLE_MAPS_API_KEY` | vacío | Clave de Google Maps (mapa con Google) |
| `GOOGLE_MAP_ID` | DEMO_MAP_ID | Map ID para marcadores avanzados |
| `GOOGLE_GEOCODING_API_KEY` | igual a la anterior | Clave para geocodificar en el servidor |
| `SMTP_HOST`, `SMTP_PORT`, `SMTP_USER`, `SMTP_PASSWORD`, `SMTP_FROM` | vacío | Si se configuran, los correos salen por SMTP real (p. ej. Gmail `smtp.gmail.com`, puerto 465, contraseña de aplicación) |

Ejemplo Gmail (Windows PowerShell):

```powershell
$env:SMTP_HOST="smtp.gmail.com"; $env:SMTP_PORT="465"
$env:SMTP_USER="equipo@gmail.com"; $env:SMTP_PASSWORD="xxxx xxxx xxxx xxxx"
.\run.bat
```

## Arquitectura: Hexagonal (Puertos y Adaptadores)

```
src/main/java/co/rutamunicipios
├── domain/            ← reglas de negocio puras (sin frameworks)
│   ├── model/         User, Email, PasswordPolicy, Origin, Session, PasswordResetToken
│   └── exception/     DomainException (sealed) y sus 7 subclases
├── application/       ← casos de uso
│   ├── port/in/       RegisterUserUseCase, AuthenticationUseCase, PasswordRecoveryUseCase, OriginUseCase
│   ├── port/out/      UserRepository, PasswordHasher, SessionRepository, ResetTokenRepository,
│   │                  EmailSender, ReverseGeocoder, TokenGenerator
│   └── service/       RegisterUserService, AuthenticationService, PasswordRecoveryService, OriginService
└── infrastructure/    ← detalles técnicos intercambiables
    ├── adapter/in/web/        ApiHandler (REST), PageHandler, DevMailboxHandler, WebServer
    ├── adapter/out/persistence JsonFileUserRepository, JsonFileResetTokenRepository, InMemorySessionRepository
    ├── adapter/out/security   Pbkdf2PasswordHasher, SecureTokenGenerator
    ├── adapter/out/mail       SmtpEmailSender, OutboxEmailSender
    ├── adapter/out/geocoding  Google Geocoding → Nominatim → respaldo sin internet
    └── config/                AppConfig, Application (raíz de composición, main)
```

## API REST

| Método | Ruta | HU | Respuestas |
|---|---|---|---|
| POST | `/api/registro` | 01 | 201, 422 (formato), 409 (duplicado) |
| POST | `/api/login` | 02 | 200 + cookie HttpOnly, 401 genérico |
| POST | `/api/logout` | 02 | 200 |
| GET | `/api/me` | 02 | 200, 401 sin sesión |
| GET | `/api/config` | 03 | 200 (proveedor de mapa y clave), 401 sin sesión |
| POST | `/api/origen/gps` | 03 | 200 (`resolved` true/false) |
| POST | `/api/origen/manual` | 03 | 200, 422 |
| POST | `/api/recuperar` | 07 | 202 (siempre igual, exista o no el correo) |
| GET | `/api/restablecer/validar?token=` | 07 | 200 `{usable}` |
| POST | `/api/restablecer` | 07 | 200, 410 (vencido/usado), 422 |

## Pruebas

35 pruebas automáticas (dominio, casos de uso, adaptadores e integración HTTP completa):

```bash
mvn test
```
