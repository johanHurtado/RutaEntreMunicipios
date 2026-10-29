package co.rutamunicipios.application.port.in;

/** Puerto de entrada HU-03: Mapa y origen. */
public interface OriginUseCase {

    record GpsOriginResult(String municipality, boolean resolved, UserView user) {}

    /** Con permiso concedido: convierte coordenadas en municipio y lo guarda. */
    GpsOriginResult setOriginFromCoordinates(String userId, double latitude, double longitude);

    /** Con permiso rechazado: el usuario escribe el municipio. */
    UserView setManualOrigin(String userId, String municipality);
}
