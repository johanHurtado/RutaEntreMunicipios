/* Adaptador de mapa del frontend (HU-03).
   inicio.js solo conoce esta interfaz:
     createMap(el, { center, zoom, onClick(lat, lng), onMove(lat, lng|null), onFallback(motivo) })
       → { provider, placeMarker(lat, lng, label), removeMarker(), getZoom() }
   Con clave configurada se usa Google Maps JavaScript API; sin clave, sin internet
   o con la clave rechazada se usa Leaflet + OpenStreetMap, para que la demo nunca se caiga. */
(function () {
  "use strict";
  const { api } = window.REM;
  const GOOGLE_TIMEOUT_MS = 10000;

  function markerElement(label) {
    const el = document.createElement("div");
    el.className = "origin-marker gm";
    el.title = label || "Origen";
    el.append(document.createElement("span"));
    return el;
  }

  /* ---------- Google Maps ---------- */
  let googleLoading = null;
  function loadGoogle(key) {
    if (window.google && google.maps && google.maps.importLibrary) return Promise.resolve();
    if (googleLoading) return googleLoading;
    googleLoading = new Promise((resolve, reject) => {
      const timer = setTimeout(() => reject(new Error("tiempo de espera agotado")), GOOGLE_TIMEOUT_MS);
      window.__remGoogleReady = () => { clearTimeout(timer); resolve(); };
      const s = document.createElement("script");
      s.src = "https://maps.googleapis.com/maps/api/js?key=" + encodeURIComponent(key) +
        "&v=weekly&loading=async&language=es&region=CO&callback=__remGoogleReady";
      s.async = true;
      s.onerror = () => { clearTimeout(timer); reject(new Error("no se pudo descargar")); };
      document.head.append(s);
    });
    return googleLoading;
  }

  async function createGoogle(el, opts, cfg) {
    await loadGoogle(cfg.googleMapsApiKey);
    const { Map } = await google.maps.importLibrary("maps");
    const { AdvancedMarkerElement } = await google.maps.importLibrary("marker");

    const map = new Map(el, {
      center: { lat: opts.center[0], lng: opts.center[1] },
      zoom: opts.zoom,
      mapId: cfg.googleMapId || "DEMO_MAP_ID",
      mapTypeControl: false,
      streetViewControl: false,
      fullscreenControl: false,
      clickableIcons: false,
      gestureHandling: "greedy"
    });
    map.addListener("click", (e) => opts.onClick(e.latLng.lat(), e.latLng.lng()));
    map.addListener("mousemove", (e) => opts.onMove(e.latLng.lat(), e.latLng.lng()));
    map.addListener("mouseout", () => opts.onMove(null, null));

    let marker = null;
    return {
      provider: "google",
      getZoom: () => map.getZoom(),
      placeMarker(lat, lng, label) {
        const position = { lat, lng };
        if (!marker) marker = new AdvancedMarkerElement({ map, position, content: markerElement(label), title: label || "Origen" });
        else { marker.position = position; marker.title = label || "Origen"; }
        map.panTo(position);
        if (map.getZoom() < 12) map.setZoom(12);
      },
      removeMarker() { if (marker) { marker.map = null; marker = null; } }
    };
  }

  /* ---------- Leaflet + OpenStreetMap (respaldo) ---------- */
  function createLeaflet(el, opts) {
    el.replaceChildren();
    const map = L.map(el, { zoomControl: true, attributionControl: true }).setView(opts.center, opts.zoom);
    L.tileLayer("https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png", {
      maxZoom: 18,
      attribution: '&copy; <a href="https://www.openstreetmap.org/copyright" target="_blank" rel="noopener">OpenStreetMap</a>'
    }).addTo(map);
    map.on("click", (e) => opts.onClick(e.latlng.lat, e.latlng.lng));
    map.on("mousemove", (e) => opts.onMove(e.latlng.lat, e.latlng.lng));
    map.on("mouseout", () => opts.onMove(null, null));

    let marker = null;
    return {
      provider: "leaflet",
      getZoom: () => map.getZoom(),
      placeMarker(lat, lng, label) {
        const icon = L.divIcon({ className: "origin-marker", html: "<span></span>", iconSize: [22, 22] });
        if (!marker) marker = L.marker([lat, lng], { icon, keyboard: false, title: label || "Origen" }).addTo(map);
        else marker.setLatLng([lat, lng]);
        map.flyTo([lat, lng], Math.max(map.getZoom(), 12), { duration: 0.8 });
      },
      removeMarker() { if (marker) { map.removeLayer(marker); marker = null; } }
    };
  }

  /* ---------- Fábrica con cambio en caliente ---------- */
  async function createMap(el, opts) {
    const res = await api("/api/config");
    const cfg = res.ok ? res.data : { mapProvider: "leaflet" };

    // Envoltorio estable: si Google falla DESPUÉS de crear el mapa (clave inválida),
    // se cambia la implementación por Leaflet sin que inicio.js lo note.
    let impl = null;
    let lastMarker = null;
    const view = {
      get provider() { return impl.provider; },
      getZoom: () => impl.getZoom(),
      placeMarker(lat, lng, label) { lastMarker = { lat, lng, label }; impl.placeMarker(lat, lng, label); },
      removeMarker() { lastMarker = null; impl.removeMarker(); }
    };
    const fallback = (reason) => {
      if (impl && impl.provider === "leaflet") return;
      impl = createLeaflet(el, opts);
      if (lastMarker) impl.placeMarker(lastMarker.lat, lastMarker.lng, lastMarker.label);
      if (opts.onFallback) opts.onFallback(reason);
    };

    if (cfg.mapProvider === "google" && cfg.googleMapsApiKey) {
      // Google llama a esta función global cuando la clave es inválida o no autorizada.
      window.gm_authFailure = () => fallback("Google Maps rechazó la clave. Revisa que la Maps JavaScript API esté habilitada y que http://localhost:8080 esté permitido.");
      try {
        impl = await createGoogle(el, opts, cfg);
      } catch (e) {
        fallback("No se pudo cargar Google Maps (" + e.message + "). Se muestra OpenStreetMap.");
      }
    } else {
      impl = createLeaflet(el, opts);
    }
    return view;
  }

  window.REM.createMap = createMap;
})();
