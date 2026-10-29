package co.rutamunicipios.support;

import co.rutamunicipios.application.port.out.EmailSender;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Captura los correos "enviados" para inspeccionarlos en las pruebas. */
public final class RecordingEmailSender implements EmailSender {
    public final List<EmailMessage> sent = new ArrayList<>();

    @Override public void send(EmailMessage message) { sent.add(message); }

    public String lastToken() {
        Matcher m = Pattern.compile("token=([A-Za-z0-9_-]+)").matcher(sent.get(sent.size() - 1).textBody());
        if (!m.find()) throw new AssertionError("El correo no contiene enlace con token");
        return m.group(1);
    }
}
