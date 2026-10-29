/* Pantallas de acceso: registro (HU-01), login (HU-02), recuperar y restablecer (HU-07). */
(function () {
  "use strict";
  const { api } = window.REM;
  const $ = (sel, root = document) => root.querySelector(sel);
  const EMAIL_RE = /^[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(\.[A-Za-z0-9-]+)*\.[A-Za-z]{2,}$/;
  const params = new URLSearchParams(location.search);

  /* ---------- utilidades de formulario ---------- */
  function setError(input, message) {
    const out = document.getElementById(input.id + "-error");
    input.setAttribute("aria-invalid", message ? "true" : "false");
    if (out) out.textContent = message || "";
  }

  function notice(id, message) {
    const box = document.getElementById(id);
    if (!box) return;
    box.hidden = !message;
    if (message) box.querySelector("span").textContent = message;
  }

  function busy(form, on) {
    const btn = form.querySelector('button[type="submit"]');
    btn.classList.toggle("is-loading", on);
    btn.disabled = on;
    btn.setAttribute("aria-busy", String(on));
  }

  function validators(form, rules) {
    // Valida al salir del campo y limpia el error en cuanto el usuario corrige.
    for (const [id, check] of Object.entries(rules)) {
      const input = document.getElementById(id);
      if (!input) continue;
      input.addEventListener("blur", () => { if (input.value) setError(input, check(input.value)); });
      input.addEventListener("input", () => {
        if (input.getAttribute("aria-invalid") === "true") setError(input, check(input.value));
      });
    }
    return function validateAll() {
      let first = null;
      for (const [id, check] of Object.entries(rules)) {
        const input = document.getElementById(id);
        const msg = check(input.value);
        setError(input, msg);
        if (msg && !first) first = input;
      }
      if (first) first.focus();
      return !first;
    };
  }

  const checks = {
    name: (v) => (v.trim().length < 2 ? "Escribe tu nombre (mínimo 2 letras)." : ""),
    email: (v) => (!v.trim() ? "Escribe tu correo." : !EMAIL_RE.test(v.trim()) ? "Revisa el formato: nombre@correo.com" : ""),
    newPassword: (v) => (v.length < 8 ? "Usa mínimo 8 caracteres." : v.length > 128 ? "Máximo 128 caracteres." : ""),
    anyPassword: (v) => (!v ? "Escribe tu contraseña." : "")
  };

  // Mostrar / ocultar contraseña
  document.querySelectorAll("[data-toggle]").forEach((btn) => {
    btn.addEventListener("click", () => {
      const input = document.getElementById(btn.dataset.toggle);
      const show = input.type === "password";
      input.type = show ? "text" : "password";
      btn.textContent = show ? "Ocultar" : "Mostrar";
      btn.setAttribute("aria-pressed", String(show));
      input.focus();
    });
  });

  // Indicador en vivo de la regla de 8 caracteres
  document.querySelectorAll("[data-rule-for]").forEach((rule) => {
    const input = document.getElementById(rule.dataset.ruleFor);
    const update = () => rule.classList.toggle("is-met", input.value.length >= 8);
    input.addEventListener("input", update);
    update();
  });

  function applyServerError(res, fieldMap = {}) {
    const field = res.data && res.data.field;
    const input = field && document.getElementById(fieldMap[field] || field);
    if (input) {
      setError(input, res.data.message);
      input.focus();
    } else {
      notice("form-notice", (res.data && res.data.message) || "Ocurrió un error. Intenta de nuevo.");
    }
  }

  /* ---------- HU-01 Registro ---------- */
  function registro() {
    const form = $("#register-form");
    const validate = validators(form, { name: checks.name, email: checks.email, password: checks.newPassword });
    form.addEventListener("submit", async (e) => {
      e.preventDefault();
      notice("form-notice", "");
      if (!validate()) return;
      busy(form, true);
      const res = await api("/api/registro", {
        method: "POST",
        body: { name: form.elements.namedItem("name").value, email: form.email.value, password: form.password.value }
      });
      busy(form, false);
      if (res.ok) {
        location.assign("/login?registro=ok&email=" + encodeURIComponent(res.data.user.email));
      } else {
        applyServerError(res);
      }
    });
  }

  /* ---------- HU-02 Login ---------- */
  function login() {
    const form = $("#login-form");
    if (params.get("email")) form.email.value = params.get("email");
    const messages = {
      sesion: ["session-notice", "Tu sesión no está activa. Inicia sesión para ver el mapa."],
      registro: ["session-notice", "Cuenta creada. Ya puedes entrar con tu correo y contraseña."],
      clave: ["session-notice", "Contraseña actualizada. Entra con la nueva; la anterior ya no funciona."]
    };
    const reason = params.get("motivo") === "sesion" ? "sesion"
      : params.get("registro") === "ok" ? "registro"
      : params.get("clave") === "ok" ? "clave" : null;
    if (reason) {
      const box = document.getElementById(messages[reason][0]);
      if (reason !== "sesion") { box.classList.remove("notice-info"); box.classList.add("notice-ok"); }
      notice(...messages[reason]);
    }
    if (form.email.value) form.password.focus();

    const validate = validators(form, { email: checks.email, password: checks.anyPassword });
    form.addEventListener("submit", async (e) => {
      e.preventDefault();
      notice("form-notice", "");
      if (!validate()) return;
      busy(form, true);
      const res = await api("/api/login", {
        method: "POST", body: { email: form.email.value, password: form.password.value }
      });
      if (res.ok) {
        location.assign(res.data.redirect || "/inicio");
        return;
      }
      busy(form, false);
      // Error genérico: no decimos si falló el correo o la contraseña.
      notice("form-notice", res.data.message || "Correo o contraseña incorrectos.");
      form.password.value = "";
      form.password.focus();
    });
  }

  /* ---------- HU-07 Solicitar enlace ---------- */
  function recuperar() {
    const form = $("#recover-form");
    if (params.get("email")) form.email.value = params.get("email");
    const validate = validators(form, { email: checks.email });
    let lastEmail = "";

    async function send(email) {
      const res = await api("/api/recuperar", { method: "POST", body: { email } });
      if (!res.ok) { notice("form-notice", res.data.message); return false; }
      $("#sent-message").textContent = res.data.message;
      $("#dev-mailbox").hidden = !res.data.devMailbox;
      return true;
    }

    form.addEventListener("submit", async (e) => {
      e.preventDefault();
      notice("form-notice", "");
      if (!validate()) return;
      busy(form, true);
      lastEmail = form.email.value.trim();
      const ok = await send(lastEmail);
      busy(form, false);
      if (ok) {
        $("#request-step").hidden = true;
        $("#sent-step").hidden = false;
        $("#sent-step").focus();
      }
    });

    $("#resend").addEventListener("click", async (e) => {
      const btn = e.currentTarget;
      btn.disabled = true;
      btn.textContent = "Enviando…";
      await send(lastEmail);
      let left = 30;
      const tick = setInterval(() => {
        btn.textContent = "Enviado. Puedes reenviar en " + left-- + " s";
        if (left < 0) { clearInterval(tick); btn.disabled = false; btn.textContent = "Enviar de nuevo"; }
      }, 1000);
    });
  }

  /* ---------- HU-07 Restablecer ---------- */
  async function restablecer() {
    const token = params.get("token") || "";
    // Saca el token de la barra de direcciones (historial / capturas de pantalla).
    history.replaceState(null, "", "/restablecer");

    const show = (id) => {
      ["checking", "expired-step", "reset-step"].forEach((s) => ($("#" + s).hidden = s !== id));
    };

    const check = token ? await api("/api/restablecer/validar?token=" + encodeURIComponent(token)) : { ok: false };
    if (!check.ok || !check.data.usable) {
      show("expired-step");
      $("#expired-step").focus();
      return;
    }
    show("reset-step");
    const form = $("#reset-form");
    form.password.focus();
    const validate = validators(form, {
      password: checks.newPassword,
      confirm: (v) => (!v ? "Repite la contraseña." : v !== form.password.value ? "Las contraseñas no coinciden." : "")
    });

    form.addEventListener("submit", async (e) => {
      e.preventDefault();
      notice("form-notice", "");
      if (!validate()) return;
      busy(form, true);
      const res = await api("/api/restablecer", { method: "POST", body: { token, password: form.password.value } });
      if (res.ok) { location.assign("/login?clave=ok"); return; }
      busy(form, false);
      if (res.status === 410) { show("expired-step"); $("#expired-step").focus(); return; }
      applyServerError(res);
    });
  }

  const pages = { registro, login, recuperar, restablecer };
  const run = pages[document.body.dataset.page];
  if (run) run();
})();
