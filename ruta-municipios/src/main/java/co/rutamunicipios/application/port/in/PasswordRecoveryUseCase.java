package co.rutamunicipios.application.port.in;

/** Puerto de entrada HU-07: Recuperación de contraseña. */
public interface PasswordRecoveryUseCase {

    /** Siempre responde igual exista o no el correo (evita enumerar cuentas). */
    void requestReset(String email);

    /** Indica si un token todavía puede usarse (para mostrar el formulario o el aviso de vencido). */
    boolean isTokenUsable(String rawToken);

    void resetPassword(String rawToken, String newPassword);
}
