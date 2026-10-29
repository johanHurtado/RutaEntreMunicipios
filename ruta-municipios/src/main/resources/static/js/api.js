/* Cliente HTTP del frontend: siempre JSON, siempre con la cookie de sesión. */
(function () {
  "use strict";

  async function api(path, { method = "GET", body } = {}) {
    const init = { method, credentials: "same-origin", headers: { Accept: "application/json" } };
    if (body !== undefined) {
      init.headers["Content-Type"] = "application/json";
      init.body = JSON.stringify(body);
    }
    try {
      const res = await fetch(path, init);
      let data = {};
      try { data = await res.json(); } catch (_) { /* respuesta sin cuerpo */ }
      return { ok: res.ok, status: res.status, data };
    } catch (_) {
      return {
        ok: false, status: 0,
        data: { error: "SIN_CONEXION", message: "No hay conexión con el servidor. Revisa que la app siga corriendo." }
      };
    }
  }

  window.REM = { api };
})();
