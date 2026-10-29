package co.rutamunicipios.infrastructure.adapter.in.web;

import co.rutamunicipios.infrastructure.adapter.out.mail.OutboxEmailSender;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static co.rutamunicipios.infrastructure.adapter.in.web.HttpSupport.*;

/** /dev/correos: bandeja de prueba para la sustentación cuando no hay SMTP configurado. */
public final class DevMailboxHandler implements HttpHandler {

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss").withZone(ZoneId.of("America/Bogota"));
    private static final Pattern LINK = Pattern.compile("(https?://\\S+)");

    private final OutboxEmailSender outbox;

    public DevMailboxHandler(OutboxEmailSender outbox) {
        this.outbox = outbox;
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        if (!ex.getRequestURI().getPath().equals("/dev/correos")) {
            sendBytes(ex, 404, "text/plain; charset=utf-8", "No encontrado".getBytes(StandardCharsets.UTF_8), false);
            return;
        }
        StringBuilder items = new StringBuilder();
        var mails = outbox.recent();
        for (var m : mails) {
            String body = escapeHtml(m.message().textBody());
            Matcher lm = LINK.matcher(body);
            body = lm.replaceAll(r -> "<a class=\"mail-link\" href=\"" + r.group(1) + "\">" + r.group(1) + "</a>");
            items.append("""
                    <article class="mail">
                      <header class="mail-head">
                        <p class="mail-to"><span>Para</span> %s</p>
                        <time>%s</time>
                      </header>
                      <h2 class="mail-subject">%s</h2>
                      <pre class="mail-body">%s</pre>
                    </article>
                    """.formatted(escapeHtml(m.message().to()), FMT.format(m.sentAt()),
                    escapeHtml(m.message().subject()), body));
        }
        if (mails.isEmpty()) {
            items.append("""
                    <div class="empty">
                      <p class="empty-title">La bandeja está vacía</p>
                      <p>Pide un enlace en <a href="/recuperar">Recuperar contraseña</a> y aparecerá aquí al instante.</p>
                    </div>""");
        }
        String html = """
                <!doctype html>
                <html lang="es"><head><meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <meta http-equiv="refresh" content="10">
                <title>Bandeja de prueba · Ruta Entre Municipios</title>
                <link rel="icon" href="/favicon.svg">
                <link rel="stylesheet" href="/css/app.css">
                </head><body class="page-mailbox">
                <main class="mailbox">
                  <p class="eyebrow">Solo desarrollo · se actualiza cada 10 s</p>
                  <h1 class="display">Bandeja de prueba</h1>
                  <p class="lede">Sin SMTP configurado, los correos de recuperación llegan aquí. Configura las variables
                  <code>SMTP_*</code> para enviarlos a un buzón real.</p>
                  %s
                </main></body></html>
                """.formatted(items);
        sendBytes(ex, 200, "text/html; charset=utf-8", html.getBytes(StandardCharsets.UTF_8), false);
    }
}
