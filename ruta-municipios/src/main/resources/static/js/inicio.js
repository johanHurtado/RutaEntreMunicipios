/* Pantalla de inicio protegida: mapa interactivo y municipio de origen (HU-03).
   Incluye la regla mínima de destino (HU-04 / BUG-01): "Buscar viaje" solo se habilita
   con destino no vacío y distinto del origen. */
(function () {
  "use strict";
  const { api } = window.REM;
  const $ = (id) => document.getElementById(id);

  const COLOMBIA = [4.57, -74.3];
  const MUNICIPIOS = [
    "Cali, Valle del Cauca", "Palmira, Valle del Cauca", "Tuluá, Valle del Cauca", "Guadalajara de Buga, Valle del Cauca",
    "Cartago, Valle del Cauca", "Jamundí, Valle del Cauca", "Yumbo, Valle del Cauca", "Buenaventura, Valle del Cauca",
    "Candelaria, Valle del Cauca", "Florida, Valle del Cauca", "Pradera, Valle del Cauca", "El Cerrito, Valle del Cauca",
    "Guacarí, Valle del Cauca", "Sevilla, Valle del Cauca", "Zarzal, Valle del Cauca", "Roldanillo, Valle del Cauca",
    "Bogotá, Bogotá D.C.", "Medellín, Antioquia", "Pereira, Risaralda", "Manizales, Caldas", "Armenia, Quindío",
    "Ibagué, Tolima", "Neiva, Huila", "Popayán, Cauca", "Pasto, Nariño", "Bucaramanga, Santander",
    "Barranquilla, Atlántico", "Cartagena de Indias, Bolívar", "Santa Marta, Magdalena", "Villavicencio, Meta"
  ];

  const els = {
    origin: $("origin"), destination: $("destination"), status: $("origin-status"), error: $("origin-error"),
    save: $("save-origin"), search: $("search-trip"), notice: $("geo-notice"), coords: $("map-coords"),
    hint: $("map-hint"), toast: $("toast")
  };
  let savedOrigin = "";
  let mapView = null;

  /* ---------- utilidades ---------- */
  const normalize = (s) => (s || "").split(",")[0].normalize("NFD").replace(/[\u0300-\u036f]/g, "")
    .toLowerCase().replace(/\s+/g, " ").trim();

  function toast(msg) {
    els.toast.textContent = msg;
    els.toast.classList.add("is-visible");
    clearTimeout(toast.t);
    toast.t = setTimeout(() => els.toast.classList.remove("is-visible"), Math.max(3200, msg.length * 60));
  }

  function setNotice(msg) {
    els.notice.hidden = !msg;
    if (msg) els.notice.querySelector("span").textContent = msg;
  }

  function setStatus(kind, text) {
    els.status.replaceChildren();
    if (kind === "loading") {
      const dot = document.createElement("span");
      dot.className = "pulse";
      els.status.append(dot, text);
      return;
    }
    if (kind) {
      const chip = document.createElement("span");
      chip.className = "chip " + kind;
      chip.textContent = kind === "gps" ? "GPS" : "Manual";
      els.status.append(chip, " " + text);
    } else if (text) {
      els.status.textContent = text;
    }
  }

  function initials(name) {
    return name.split(/\s+/).filter(Boolean).slice(0, 2).map((p) => p[0].toUpperCase()).join("");
  }

  /* ---------- HU-04 mínimo / BUG-01 ---------- */
  function refreshSearch() {
    const o = normalize(els.origin.value);
    const d = normalize(els.destination.value);
    const same = d && o && d === o;
    els.search.disabled = !o || !d || same;
    $("dest-hint").textContent = same ? "El destino no puede ser igual al origen." : "Debe ser distinto al origen.";
    $("dest-hint").style.color = same ? "var(--danger)" : "";
    els.save.hidden = !els.origin.value.trim() || els.origin.value.trim() === savedOrigin;
  }

  /* ---------- mapa (Google Maps o Leaflet, ver map-provider.js) ---------- */
  async function initMap() {
    mapView = await window.REM.createMap($("map"), {
      center: COLOMBIA,
      zoom: 6,
      onClick: (lat, lng) => resolveCoordinates(lat, lng, "map"),
      onMove: (lat, lng) => { els.coords.textContent = lat == null ? "" : lat.toFixed(4) + ", " + lng.toFixed(4); },
      onFallback: (reason) => { console.warn("[Mapa]", reason); toast(reason); $("map-provider").textContent = "OpenStreetMap"; }
    });
    $("map-provider").textContent = mapView.provider === "google" ? "Google Maps" : "OpenStreetMap";
  }

  function placeMarker(lat, lon, label) {
    mapView.placeMarker(lat, lon, label);
    els.hint.hidden = true;
  }

  async function resolveCoordinates(lat, lon, via) {
    placeMarker(lat, lon);
    setStatus("loading", via === "gps" ? "Buscando tu municipio…" : "Identificando el municipio del punto…");
    const res = await api("/api/origen/gps", { method: "POST", body: { lat, lon } });
    if (res.status === 401) return goLogin();
    if (!res.ok) { setStatus(null, ""); els.error.textContent = res.data.message; return; }
    els.error.textContent = "";
    if (res.data.resolved) {
      savedOrigin = res.data.municipality;
      els.origin.value = savedOrigin;
      els.origin.setAttribute("aria-invalid", "false");
      setStatus("gps", via === "gps" ? "Detectado con tu ubicación" : "Elegido en el mapa");
      placeMarker(lat, lon, savedOrigin);
      if (via === "gps") setNotice("");
    } else {
      setStatus(null, "No identificamos un municipio en ese punto. Escríbelo arriba.");
      els.origin.focus();
    }
    refreshSearch();
  }

  /* ---------- geolocalización ---------- */
  function requestLocation(userInitiated) {
    if (!("geolocation" in navigator)) {
      denied("Tu navegador no permite obtener la ubicación. Escribe tu municipio de origen.");
      return;
    }
    setStatus("loading", "Esperando permiso de ubicación…");
    navigator.geolocation.getCurrentPosition(
      (pos) => resolveCoordinates(pos.coords.latitude, pos.coords.longitude, "gps"),
      (err) => {
        if (err.code === err.PERMISSION_DENIED) {
          denied("No diste permiso de ubicación. Escribe tu municipio de origen. Para activarlo, toca el candado junto a la dirección.");
        } else {
          denied("No pudimos obtener tu ubicación ahora. Escribe tu municipio o toca el mapa.");
        }
      },
      { enableHighAccuracy: false, timeout: userInitiated ? 15000 : 10000, maximumAge: 300000 }
    );
  }

  function denied(message) {
    setNotice(message);
    setStatus(null, "");
    els.origin.focus();
  }

  /* ---------- origen manual ---------- */
  async function saveManual(e) {
    e.preventDefault();
    const value = els.origin.value.trim();
    if (value.length < 3) {
      els.error.textContent = "Escribe el municipio de origen (mínimo 3 letras).";
      els.origin.setAttribute("aria-invalid", "true");
      els.origin.focus();
      return;
    }
    const res = await api("/api/origen/manual", { method: "POST", body: { municipality: value } });
    if (res.status === 401) return goLogin();
    if (!res.ok) {
      els.error.textContent = res.data.message;
      els.origin.setAttribute("aria-invalid", "true");
      return;
    }
    els.error.textContent = "";
    els.origin.setAttribute("aria-invalid", "false");
    savedOrigin = res.data.user.origin.municipality;
    els.origin.value = savedOrigin;
    setStatus("manual", "Escrito por ti");
    mapView.removeMarker();
    toast("Origen guardado: " + savedOrigin);
    refreshSearch();
  }

  function goLogin() {
    location.assign("/login?motivo=sesion");
  }

  /* ---------- arranque ---------- */
  async function start() {
    const me = await api("/api/me");
    if (!me.ok) return goLogin(); // doble protección: el servidor ya redirige sin sesión

    const user = me.data.user;
    $("user-name").textContent = user.name;
    $("user-mail").textContent = user.email;
    $("avatar").textContent = initials(user.name);
    document.title = "Hola, " + user.name.split(" ")[0] + " · Ruta Entre Municipios";

    const list = $("municipios");
    MUNICIPIOS.forEach((m) => { const o = document.createElement("option"); o.value = m; list.append(o); });

    await initMap();

    if (user.origin) {
      savedOrigin = user.origin.municipality;
      els.origin.value = savedOrigin;
      if (user.origin.source === "GPS" && user.origin.lat != null) {
        placeMarker(user.origin.lat, user.origin.lon, savedOrigin);
        setStatus("gps", "Guardado en tu cuenta");
      } else {
        setStatus("manual", "Guardado en tu cuenta");
      }
    }

    // Pide la ubicación al entrar (si el permiso ya está negado, avisa sin volver a preguntar).
    let state = "prompt";
    try { state = (await navigator.permissions.query({ name: "geolocation" })).state; } catch (_) { /* Safari antiguo */ }
    if (state === "denied") denied("El permiso de ubicación está bloqueado. Escribe tu municipio de origen o toca el mapa.");
    else if (!user.origin || user.origin.source === "GPS") requestLocation(false);

    refreshSearch();
  }

  $("locate").addEventListener("click", () => { setNotice(""); requestLocation(true); });
  $("origin-form").addEventListener("submit", saveManual);
  els.origin.addEventListener("input", () => { els.error.textContent = ""; refreshSearch(); });
  els.destination.addEventListener("input", refreshSearch);
  els.search.addEventListener("click", () =>
    toast("Listo el tramo. La búsqueda de viajes llega en el siguiente incremento (HU-05)."));
  $("logout").addEventListener("click", async () => {
    await api("/api/logout", { method: "POST", body: {} });
    location.assign("/login");
  });

  start();
})();
