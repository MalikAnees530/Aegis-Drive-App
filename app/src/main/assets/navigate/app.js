// Aegis Drive - Golden Master Premium Engine (GIS & NAVIGATION OVERHAUL - QA FIXED)
const map = new maplibregl.Map({
    container: 'map',
    style: 'https://tiles.openfreemap.org/styles/liberty',
    center: [73.0479, 33.6844], 
    zoom: 14, pitch: 45, antialias: true, attributionControl: false,
    doubleClickZoom: false 
});

// 🚀 ENGINE STATE
let userMarker = null;
let currentPos = [73.0479, 33.6844];
let currentHeading = 0;
let isNavigating = false;
let isCameraFollow = true;
let isEditMode = false;
let targetPos = null;
let lastRouteData = null;
let lastUserSnapPoint = null;
let poiMarkers = [];
let lastInteractionTime = 0;

// 🗝️ API CONFIG
const TOMTOM_KEY = "YOUR_TOMTOM_API_KEY"; 

// 🚀 NAVIGATION BEAM
const beamEl = document.createElement('div');
beamEl.className = 'nav-beam';
beamEl.innerHTML = '<div class="nav-arrow"></div>';

map.on('load', () => {
    userMarker = new maplibregl.Marker({ element: beamEl, rotationAlignment: 'map' }).setLngLat(currentPos).addTo(map);
    map.addSource('route', { type: 'geojson', data: { type: 'Feature', geometry: { type: 'LineString', coordinates: [] } } });
    map.addLayer({ id: 'route', type: 'line', source: 'route', layout: { 'line-join': 'round', 'line-cap': 'round' }, paint: { 'line-color': '#38BDF8', 'line-width': 8, 'line-opacity': 0.9 } });
    map.addControl(new maplibregl.NavigationControl({ showCompass: false }), 'top-right');
});

// 🛰️ TASK 1 & 4: ERADICATE CAMERA JITTER & MANUAL OVERRIDE
map.on('dragstart', () => { 
    lastInteractionTime = Date.now();
    isCameraFollow = false; 
});

map.on('zoomstart', () => {
    lastInteractionTime = Date.now();
    isCameraFollow = false;
});

// Resets follow mode after 5 seconds of inactivity (Increased for better UX)
const checkInteractionTimeout = () => {
    if (!isCameraFollow && (Date.now() - lastInteractionTime > 5000)) {
        isCameraFollow = true;
    }
};

const recenterMap = () => { 
    isCameraFollow = true; 
    lastInteractionTime = 0;
    map.flyTo({ center: currentPos, zoom: 19, pitch: 65, bearing: currentHeading, duration: 1000 }); 
};

document.getElementById('btnRecenter').onclick = recenterMap;
document.getElementById('btnRecenterNav').onclick = recenterMap;

// 🔍 TASK 2 & 3: DYNAMIC BIAS & EMPTY STATES
const etSearch = document.getElementById('etSearch');
const suggestionsList = document.getElementById('suggestions');
let searchAbortController = null;

function optimizeQuery(raw) {
    if (!raw) return "";
    let q = raw.toLowerCase().trim();
    q = q.replace(/\bnarayn bhagwan\b/g, "naran kaghan")
         .replace(/\bnaran khaghan\b/g, "naran kaghan")
         .replace(/\bmurre road\b/g, "murree road rawalpindi");

    const sectorRegex = /\b([a-i])[\s\/\-]*(\d{1,2})[\s\/\-]*([1-4])?\b/i;
    let match = q.match(sectorRegex);
    if (match) {
        let letter = match[1].toUpperCase();
        let sectorNum = match[2];
        let subSector = match[3] || "";
        if (parseInt(sectorNum) <= 18) {
            let formatted = `${letter}-${sectorNum}${subSector ? '/' + subSector : ''} Islamabad`;
            q = q.replace(match[0], formatted);
        }
    }
    if (!q.includes("pakistan") && !q.includes("islamabad") && !q.includes("lahore") && !q.includes("karachi") && !q.includes("rawalpindi")) {
        q += " Pakistan";
    }
    return q;
}

// Generates dynamic viewbox (proximity bias) around current coordinates
function getDynamicViewbox() {
    const lon = currentPos[0] || 73.0479;
    const lat = currentPos[1] || 33.6844;
    const offset = 0.5; // Approx 50km radius
    return `${lon - offset},${lat - offset},${lon + offset},${lat + offset}`;
}

async function performSearch(query) {
    if (!query) return;

    if (searchAbortController) searchAbortController.abort();
    searchAbortController = new AbortController();

    if (window.Android && !window.Android.isNetworkAvailable()) {
        showCustomToast("📶 Offline. Searching local Aegis Cache...");
        const history = window.Android.getSearchHistory();
        if (history) {
            const matches = history.split(";").filter(h => h.toLowerCase().includes(query.toLowerCase()));
            if (matches.length > 0) {
                const parts = matches[0].split("|");
                handleDestinationSelected([parseFloat(parts[2]), parseFloat(parts[1])], parts[0]);
                return;
            }
        }
        showCustomToast("❌ Destination not in local cache.");
        return;
    }

    const q = optimizeQuery(query);
    try {
        const safeQuery = encodeURIComponent(q.trim());
        const viewbox = getDynamicViewbox();
        const url = `https://nominatim.openstreetmap.org/search?format=json&q=${safeQuery}&countrycodes=pk&addressdetails=1&limit=5&viewbox=${viewbox}&bounded=0&extratags=1`;
        
        const res = await fetch(url, { signal: searchAbortController.signal });
        const data = await res.json();
        
        if (data && data.length > 0) {
            const bestResult = data[0];
            const lat = parseFloat(bestResult.lat);
            const lon = parseFloat(bestResult.lon);
            const name = bestResult.display_name;
            if (window.Android) window.Android.saveSearchHistory(name, lat, lon);
            handleDestinationSelected([lon, lat], name);
        } else {
            renderEmptyState();
        }
    } catch (e) {
        if (e.name !== 'AbortError') showCustomToast("❌ Search failed");
    }
}

etSearch.oninput = debounce(async (e) => {
    const rawVal = e.target.value;
    if (rawVal.length < 2) { suggestionsList.classList.add('hidden'); return; }

    if (searchAbortController) searchAbortController.abort();
    searchAbortController = new AbortController();

    if (window.Android && !window.Android.isNetworkAvailable()) {
        const history = window.Android.getSearchHistory();
        if (history) {
            const matches = history.split(";").filter(h => h.toLowerCase().includes(rawVal.toLowerCase()));
            renderSuggestions(matches.map(m => {
                const p = m.split("|");
                return { display_name: p[0], lat: p[1], lon: p[2], isOffline: true };
            }));
        }
        return;
    }

    const q = optimizeQuery(rawVal);
    try {
        const safeQuery = encodeURIComponent(q.trim());
        const viewbox = getDynamicViewbox();
        const url = `https://nominatim.openstreetmap.org/search?format=json&q=${safeQuery}&countrycodes=pk&addressdetails=1&limit=10&viewbox=${viewbox}`;

        const res = await fetch(url, { signal: searchAbortController.signal });
        const data = await res.json();
        renderSuggestions(data);
    } catch (e) { 
        if (e.name !== 'AbortError') suggestionsList.classList.add('hidden'); 
    }
}, 300);

function renderSuggestions(data) {
    suggestionsList.innerHTML = '';
    if (data && data.length > 0) {
        data.forEach(item => {
            const div = document.createElement('div');
            div.className = 'suggestion-item';
            const name = item.display_name.split(',')[0];
            const addr = item.display_name;
            const icon = item.isOffline ? '💾' : '📍';
            div.innerHTML = `<div class="sugg-icon">${icon}</div><div class="sugg-text"><span class="sugg-name">${name}</span><span class="sugg-addr">${addr}</span></div>`;
            div.onclick = () => { 
                etSearch.value = name; 
                suggestionsList.classList.add('hidden'); 
                handleDestinationSelected([parseFloat(item.lon), parseFloat(item.lat)], name); 
            };
            suggestionsList.appendChild(div);
        });
        suggestionsList.classList.remove('hidden');
    } else {
        renderEmptyState();
    }
}

function renderEmptyState() {
    suggestionsList.innerHTML = `
        <div class="suggestion-item empty-state">
            <div class="sugg-icon">🔍</div>
            <div class="sugg-text">
                <span class="sugg-name">Location not found</span>
                <span class="sugg-addr">Try a different term or check spelling</span>
            </div>
        </div>
    `;
    suggestionsList.classList.remove('hidden');
}

// Input Interactions
etSearch.onkeydown = (e) => { if (e.key === 'Enter') { performSearch(etSearch.value); suggestionsList.classList.add('hidden'); etSearch.blur(); } };
etSearch.addEventListener('search', () => { performSearch(etSearch.value); suggestionsList.classList.add('hidden'); });
document.getElementById('btnSearchSubmit').onclick = () => { performSearch(etSearch.value); suggestionsList.classList.add('hidden'); };
document.getElementById('btnVoice').onclick = () => { if (window.Android) window.Android.startVoiceRecognition(); };

window.onVoiceResult = (text) => { 
    etSearch.value = text;
    performSearch(text); 
    const optimized = optimizeQuery(text);
    const uiText = optimized.replace(" Islamabad", "").replace(" Pakistan", "").toUpperCase();
    etSearch.value = uiText;
};

// 🗺️ ROUTING & CORE ENGINE
let routingAbortController = null;

function handleDestinationSelected(pos, name) {
    if (!pos || pos.length < 2) return;
    targetPos = pos;
    clearPOIs();
    const label = document.createElement('div');
    label.className = 'marker-bubble';
    label.innerText = `📍 ${name.split(',')[0]}`;
    const m = new maplibregl.Marker({ element: label }).setLngLat(pos).addTo(map);
    poiMarkers.push(m);
    document.getElementById('navStreet').innerText = name.split(',')[0];
    document.getElementById('routingCard').classList.remove('hidden');
    map.flyTo({ center: pos, zoom: 16, pitch: 0, duration: 1500 });
    planRoute(currentPos, pos);
}

async function planRoute(start, dest) {
    if (!start || !dest) return;
    if (window.Android && !window.Android.isNetworkAvailable()) {
        showCustomToast("📶 Routing requires active connection.");
        return;
    }

    if (routingAbortController) routingAbortController.abort();
    routingAbortController = new AbortController();

    try {
        const url = `https://router.project-osrm.org/route/v1/driving/${start[0]},${start[1]};${dest[0]},${dest[1]}?overview=full&geometries=geojson&steps=true&continue_straight=true`;
        const res = await fetch(url, { signal: routingAbortController.signal });
        const data = await res.json();
        
        if (data.code === 'Ok' && data.routes && data.routes.length > 0) {
            lastRouteData = data.routes[0];
            const source = map.getSource('route');
            if (source) source.setData(lastRouteData.geometry);
            lastUserSnapPoint = lastRouteData.geometry.coordinates[0];
            syncDottedLine();
            updateETADisplay(lastRouteData);
            if (!isNavigating) {
                const bounds = lastRouteData.geometry.coordinates.reduce((acc, coord) => acc.extend(coord), new maplibregl.LngLatBounds(currentPos, currentPos));
                map.fitBounds(bounds, { padding: 100, duration: 1500 });
            }
        } else {
            showCustomToast("❌ Route calculation failed.");
        }
    } catch (e) {
        if (e.name !== 'AbortError') showCustomToast("❌ Routing error.");
    }
}

function syncDottedLine() {
    if (!lastUserSnapPoint || !currentPos) return;
    const geo = { type: 'Feature', geometry: { type: 'LineString', coordinates: [currentPos, lastUserSnapPoint] } };
    const source = map.getSource('route-dotted');
    if (!source) {
        map.addSource('route-dotted', { type: 'geojson', data: geo });
        map.addLayer({ id: 'route-dotted', type: 'line', source: 'route-dotted', paint: { 'line-color': '#38BDF8', 'line-width': 5, 'line-dasharray': [1, 2] } });
    } else { source.setData(geo); }
}

function updateETADisplay(r = null) {
    const route = r || lastRouteData;
    if (!route || !route.legs || route.legs.length === 0) return;
    
    const distKm = route.distance / 1000;
    const timeMin = Math.round(route.duration / 60);
    const distText = distKm < 1.0 ? Math.round(route.distance) + " m" : distKm.toFixed(1) + " km";
    
    const elDist = document.getElementById('navDistance');
    const elTime = document.getElementById('navTime');
    if (elDist) elDist.innerText = distText;
    if (elTime) elTime.innerText = timeMin + " min";

    if (isNavigating) {
        const elTimeCount = document.getElementById('navTimeCountdown');
        const elDistPrecise = document.getElementById('navDistancePrecise');
        const elArrival = document.getElementById('navArrivalTime');
        if (elTimeCount) elTimeCount.innerText = timeMin + " min";
        if (elDistPrecise) elDistPrecise.innerText = distText;
        
        const arrival = new Date(); 
        arrival.setSeconds(arrival.getSeconds() + route.duration);
        if (elArrival) elArrival.innerText = arrival.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });

        if (route.legs[0].steps && route.legs[0].steps.length > 0) {
            const s = route.legs[0].steps[0];
            const elNextStreet = document.getElementById('nextStreetName');
            const elDistTurn = document.getElementById('distanceToTurn');
            const elTurnIcon = document.getElementById('nextTurnIcon');
            if (elNextStreet) elNextStreet.innerText = s.name || "Toward Destination";
            if (elDistTurn) elDistTurn.innerText = "In " + (s.distance < 1000 ? Math.round(s.distance) + " m" : (s.distance/1000).toFixed(1) + " km");
            if (elTurnIcon) elTurnIcon.innerText = getManeuverIcon(s.maneuver.modifier);
        }
    }
}

function getManeuverIcon(m) {
    switch(m) {
        case 'left': return '⬅️'; case 'right': return '➡️'; case 'slight left': return '↖️'; case 'slight right': return '↗️';
        case 'sharp left': return '↩️'; case 'sharp right': return '↪️'; case 'uturn': return '🔄'; default: return '⬆️';
    }
}

// 🗺️ NAVIGATION SYSTEM WINDOWS
let mapInitialized = false;

window.initializeMapCenter = function(lat, lng) {
    if (!mapInitialized) {
        currentPos = [lng, lat];
        map.jumpTo({ center: currentPos, zoom: 15 });
        mapInitialized = true;
    }
};

window.updateRealTimeTracking = function(lat, lng, heading) {
    currentPos = [lng, lat];
    currentHeading = heading;
    
    if (!userMarker) {
        userMarker = new maplibregl.Marker({ element: beamEl, rotationAlignment: 'map' }).setLngLat(currentPos).addTo(map);
    } else {
        userMarker.setLngLat(currentPos);
        userMarker.setRotation(heading); 
    }
    
    syncDottedLine();
    checkInteractionTimeout();

    // 🚀 TASK 1: SMOOTH CAMERA LERP (Only if follow is active)
    if (isNavigating && isCameraFollow) {
        map.easeTo({
            center: currentPos,
            bearing: heading,
            duration: 800, // Smoother lerp duration
            easing: (t) => t
        });
        updateETADisplay();
    }
};

// 🚀 NAVIGATION ACTIONS
document.getElementById('btnStartDrive').onclick = () => {
    isNavigating = true; isCameraFollow = true;
    document.getElementById('searchContainer').classList.add('hidden');
    document.getElementById('routingCard').classList.add('hidden');
    document.getElementById('navDashboard').classList.remove('hidden');
    map.jumpTo({ center: currentPos, zoom: 19, pitch: 65, bearing: currentHeading });
    updateETADisplay();
};

document.getElementById('btnCloseRouting').onclick = stopNavigation;
document.getElementById('btnStopNav').onclick = stopNavigation;
document.getElementById('btnDirections').onclick = () => { if (targetPos) { const b = [currentPos, targetPos].reduce((acc, c) => acc.extend(c), new maplibregl.LngLatBounds(currentPos, currentPos)); map.fitBounds(b, { padding: 100, duration: 1500 }); } };

function stopNavigation() {
    isNavigating = false; targetPos = null; clearPOIs();
    map.getSource('route').setData({ type: 'Feature', geometry: { type: 'LineString', coordinates: [] } });
    if (map.getSource('route-dotted')) map.getSource('route-dotted').setData({ type: 'Feature', geometry: { type: 'LineString', coordinates: [] } });
    document.getElementById('navDashboard').classList.add('hidden');
    document.getElementById('routingCard').classList.add('hidden');
    document.getElementById('searchContainer').classList.remove('hidden');
    map.easeTo({ pitch: 45, zoom: 14, bearing: 0, duration: 1000 });
}

function clearPOIs() { poiMarkers.forEach(m => m.remove()); poiMarkers = []; }
function debounce(f, w) { let t; return (...a) => { clearTimeout(t); t = setTimeout(() => f(...a), w); }; }
function showCustomToast(msg) { const t = document.getElementById('customToast'); t.innerText = msg; t.classList.add('visible'); setTimeout(() => t.classList.remove('visible'), 3000); }
