package co.rutamunicipios.application.service;

import co.rutamunicipios.application.port.out.EmailSender.EmailMessage;

/** Plantillas de correo (texto + HTML). */
final class EmailTemplates {

    private EmailTemplates() {
    }

    static EmailMessage passwordReset(String to, String name, String link, long minutes) {
        String text = """
                Hola %s,

                Recibimos una solicitud para cambiar la contraseña de tu cuenta en Ruta Entre Municipios.
                Abre este enlace para elegir una nueva (vence en %d minutos):

                %s

                Si no fuiste tú, ignora este correo: tu contraseña actual sigue funcionando.
                """.formatted(name, minutes, link);

        String html = """
                <div style="font-family:Arial,Helvetica,sans-serif;max-width:520px;margin:auto;color:#14261e">
                  <p style="font-size:12px;letter-spacing:.12em;text-transform:uppercase;color:#4d6b5c">Ruta Entre Municipios</p>
                  <h1 style="font-size:22px;margin:0 0 12px">Cambia tu contraseña</h1>
                  <p>Hola %s, recibimos una solicitud para cambiar tu contraseña.</p>
                  <p><a href="%s" style="display:inline-block;background:#14261e;color:#ffd166;padding:12px 20px;
                     border-radius:8px;text-decoration:none;font-weight:bold">Elegir nueva contraseña</a></p>
                  <p style="font-size:13px;color:#4d6b5c">El enlace vence en %d minutos y solo se puede usar una vez.
                     Si no fuiste tú, ignora este correo.</p>
                </div>
                """.formatted(escape(name), link, minutes);

        return new EmailMessage(to, "Recupera tu contraseña · Ruta Entre Municipios", text, html);
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
