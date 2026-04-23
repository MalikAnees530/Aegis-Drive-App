// Aegis Drive - Golden Master Premium Engine (Lead UI/UX DESIGNER EDITION)
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
let isNavigating = false;
let isCameraFollow = true;
let isEditMode = false;
let targetPos = null;
let lastRouteData = null;
let lastUserSnapPoint = null;
let poiMarkers = [];

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

// 🛰️ CAMERA SYNC & RECENTER
map.on('dragstart', () => { if (isNavigating) isCameraFollow = false; });
const recenterMap = () => { isCameraFollow = true; map.flyTo({ center: currentPos, zoom: 19, pitch: 65, bearing: userMarker ? userMarker.getRotation() : 0, duration: 1000 }); };
document.getElementById('btnRecenter').onclick = recenterMap;
document.getElementById('btnRecenterNav').onclick = recenterMap;

// ---------------------------------------------------------
// 🏠 HOME ACTIONS
// ---------------------------------------------------------

const btnHome = document.getElementById('btnHome');
const homeMenu = document.getElementById('homeMenu');

btnHome.onclick = (e) => { e.stopPropagation(); homeMenu.classList.toggle('hidden'); };
document.addEventListener('click', () => homeMenu.classList.add('hidden'));

document.getElementById('menuEditHome').onclick = () => { isEditMode = true; document.getElementById('editOverlay').classList.remove('hidden'); homeMenu.classList.add('hidden'); };
document.getElementById('menuNavigateHome').onclick = () => { if (window.Android) window.Android.triggerNavigateHome(); homeMenu.classList.add('hidden'); };
document.getElementById('btnCancelEdit').onclick = () => { isEditMode = false; document.getElementById('editOverlay').classList.add('hidden'); };

map.on('click', (e) => {
    if (!isEditMode) return;
    isEditMode = false;
    document.getElementById('editOverlay').classList.add('hidden');
    if (window.Android) {
        window.Android.saveHomeLocation(e.lngLat.lat, e.lngLat.lng);
        showCustomToast("🏠 Home Location Saved Successfully");
        handleDestinationSelected([e.lngLat.lng, e.lngLat.lat], "Home");
    }
});

// ---------------------------------------------------------
// 🔍 PRECISION SEARCH ENGINE (PAKISTAN WIDE + SECTOR FIX)
// ---------------------------------------------------------

const etSearch = document.getElementById('etSearch');
const suggestionsList = document.getElementById('suggestions');

function optimizeQuery(raw) {
    let q = raw.toLowerCase().trim();
    
    // 🚀 VOICE CORRECTION LAYER (Common misrecognitions)
    q = q.replace(/\bnarayn bhagwan\b/g, "naran kaghan")
         .replace(/\bnaran khaghan\b/g, "naran kaghan")
         .replace(/\bmurre road\b/g, "murree road rawalpindi");

    // 🚀 PRECISION FIX: Islamabad Sector Normalization (Handles G11, F10, G13, G9/2, etc.)
    // Optimized regex to capture 1-2 digit sectors and optional subsectors
    const sectorRegex = /\b([a-i])[\s\/\-]*(\d{1,2})[\s\/\-]*([1-4])?\b/i;
    let match = q.match(sectorRegex);
    
    if (match) {
        let letter = match[1].toUpperCase();
        let sectorNum = match[2];
        let subSector = match[3] || "";
        
        // Logical filter: Islamabad sectors only go up to 17
        if (parseInt(sectorNum) <= 18) {
            let formatted = `${letter}-${sectorNum}${subSector ? '/' + subSector : ''} Islamabad`;
            q = q.replace(match[0], formatted);
        }
    }
    
    // Global Pakistan Bias
    if (!q.includes("pakistan") && !q.includes("islamabad") && !q.includes("lahore") && !q.includes("karachi") && !q.includes("rawalpindi")) {
        q += " Pakistan";
    }
    return q;
}

async function performSearch(query) {
    if (!query) return;
    const q = optimizeQuery(query);
    try {
        // 🚀 TASK 2: High-Precision, Region-Locked Geocoding
        const safeQuery = encodeURIComponent(q.trim());
        const url = `https://nominatim.openstreetmap.org/search?format=json&q=${safeQuery}&countrycodes=pk&addressdetails=1&limit=5`;
        
        const res = await fetch(url);
        const data = await res.json();
        
        if (data && data.length > 0) {
            const bestResult = data[0];
            const lat = parseFloat(bestResult.lat);
            const lon = parseFloat(bestResult.lon);
            const name = bestResult.display_name;
            handleDestinationSelected([lon, lat], name);
        } else {
            showCustomToast("❌ Location not found in Pakistan");
        }
    } catch (e) {
        showCustomToast("❌ Search failed");
    }
}

etSearch.oninput = debounce(async (e) => {
    const rawVal = e.target.value;
    if (rawVal.length < 2) { suggestionsList.classList.add('hidden'); return; }
    const q = optimizeQuery(rawVal);
    try {
        const safeQuery = encodeURIComponent(q.trim());
        const url = `https://nominatim.openstreetmap.org/search?format=json&q=${safeQuery}&countrycodes=pk&addressdetails=1&limit=10`;

        const res = await fetch(url);
        const data = await res.json();
        suggestionsList.innerHTML = '';
        
        if (data && data.length > 0) {
            data.forEach(item => {
                const div = document.createElement('div');
                div.className = 'suggestion-item';
                const name = item.display_name.split(',')[0];
                const addr = item.display_name;
                div.innerHTML = `<div class="sugg-icon">📍</div><div class="sugg-text"><span class="sugg-name">${name}</span><span class="sugg-addr">${addr}</span></div>`;
                div.onclick = () => { etSearch.value = name; suggestionsList.classList.add('hidden'); handleDestinationSelected([parseFloat(item.lon), parseFloat(item.lat)], name); };
                suggestionsList.appendChild(div);
            });
            suggestionsList.classList.remove('hidden');
        } else {
            suggestionsList.classList.add('hidden');
        }
    } catch (e) { suggestionsList.classList.add('hidden'); }
}, 300);

// Input Interactions
etSearch.onkeydown = (e) => { if (e.key === 'Enter') { performSearch(etSearch.value); suggestionsList.classList.add('hidden'); etSearch.blur(); } };
etSearch.addEventListener('search', () => { performSearch(etSearch.value); suggestionsList.classList.add('hidden'); });
document.getElementById('btnSearchSubmit').onclick = () => { performSearch(etSearch.value); suggestionsList.classList.add('hidden'); };
document.getElementById('btnVoice').onclick = () => { if (window.Android) window.Android.startVoiceRecognition(); };

window.onVoiceResult = (text) => { 
    etSearch.value = text;
    performSearch(text); 
    // Format UI display based on logic
    const optimized = optimizeQuery(text);
    const uiText = optimized.replace(" Islamabad", "").replace(" Pakistan", "").toUpperCase();
    etSearch.value = uiText;
};

// ---------------------------------------------------------
// 🗺️ ROUTING & CORE ENGINE
// ---------------------------------------------------------

map.on('dblclick', (e) => {
    if (isEditMode) return;
    const features = map.queryRenderedFeatures(e.point);
    let name = features.length > 0 ? (features[0].properties.name || "Selected Location") : "Selected Location";
    handleDestinationSelected([e.lngLat.lng, e.lngLat.lat], name);
});

function handleDestinationSelected(pos, name) {
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
    try {
        const url = `https://router.project-osrm.org/route/v1/driving/${start[0]},${start[1]};${dest[0]},${dest[1]}?overview=full&geometries=geojson&steps=true&continue_straight=true`;
        const res = await fetch(url);
        const data = await res.json();
        if (data.code === 'Ok' && data.routes.length > 0) {
            lastRouteData = data.routes[0];
            map.getSource('route').setData(lastRouteData.geometry);
            lastUserSnapPoint = lastRouteData.geometry.coordinates[0];
            syncDottedLine();
            updateETADisplay(lastRouteData);
            if (!isNavigating) {
                const bounds = lastRouteData.geometry.coordinates.reduce((acc, coord) => acc.extend(coord), new maplibregl.LngLatBounds(currentPos, currentPos));
                map.fitBounds(bounds, { padding: 100, duration: 1500 });
            }
        }
    } catch (e) {}
}

function syncDottedLine() {
    if (!lastUserSnapPoint) return;
    const geo = { type: 'Feature', geometry: { type: 'LineString', coordinates: [currentPos, lastUserSnapPoint] } };
    if (!map.getSource('route-dotted')) {
        map.addSource('route-dotted', { type: 'geojson', data: geo });
        map.addLayer({ id: 'route-dotted', type: 'line', source: 'route-dotted', paint: { 'line-color': '#38BDF8', 'line-width': 5, 'line-dasharray': [1, 2] } });
    } else { map.getSource('route-dotted').setData(geo); }
}

function updateETADisplay(r = null) {
    const route = r || lastRouteData;
    if (!route) return;
    const distKm = route.distance / 1000;
    const timeMin = Math.round(route.duration / 60);
    const distText = distKm < 1.0 ? Math.round(route.distance) + " m" : distKm.toFixed(1) + " km";
    document.getElementById('navDistance').innerText = distText;
    document.getElementById('navTime').innerText = timeMin + " min";
    if (isNavigating) {
        document.getElementById('navTimeCountdown').innerText = timeMin + " min";
        document.getElementById('navDistancePrecise').innerText = distText;
        const arrival = new Date(); arrival.setSeconds(arrival.getSeconds() + route.duration);
        document.getElementById('navArrivalTime').innerText = arrival.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
        if (route.legs && route.legs[0].steps.length > 0) {
            const s = route.legs[0].steps[0];
            document.getElementById('nextStreetName').innerText = s.name || "Toward Destination";
            document.getElementById('distanceToTurn').innerText = "In " + (s.distance < 1000 ? Math.round(s.distance) + " m" : (s.distance/1000).toFixed(1) + " km");
            document.getElementById('nextTurnIcon').innerText = getManeuverIcon(s.maneuver.modifier);
        }
    }
}

function getManeuverIcon(m) {
    switch(m) {
        case 'left': return '⬅️'; case 'right': return '➡️'; case 'slight left': return '↖️'; case 'slight right': return '↗️';
        case 'sharp left': return '↩️'; case 'sharp right': return '↪️'; case 'uturn': return '🔄'; default: return '⬆️';
    }
}

// 🚀 TASK 2: Map Initialization & Free Camera Panning
let mapInitialized = false;

window.initializeMapCenter = function(lat, lng) {
    if (!mapInitialized) {
        currentPos = [lng, lat];
        map.jumpTo({ center: currentPos, zoom: 15 }); // Instantly snap, no slow animation
        mapInitialized = true;
    }
};

window.updateRealTimeTracking = function(lat, lng, heading) {
    currentPos = [lng, lat];
    
    // 1. Update or Create the Marker
    if (!userMarker) {
        userMarker = new maplibregl.Marker({ element: beamEl, rotationAlignment: 'map' })
            .setLngLat(currentPos)
            .addTo(map);
    } else {
        // Just update the marker's position and direction. 
        userMarker.setLngLat(currentPos);
        userMarker.setRotation(heading); 
    }
    
    syncDottedLine();

    // 🛑 CRITICAL FIX: Removed map.easeTo() camera movement from the loop.
    // This stops the "Stuck Camera" bug and allows the user to freely pan/zoom.
    if (isNavigating) {
        updateETADisplay();
    }
};

// Keep old bridges for compatibility
window.updateUserLocation = function(lat, lng, heading) {
    window.updateRealTimeTracking(lat, lng, heading);
};

window.updateLocation = function(lat, lng, speed, heading) {
    window.updateRealTimeTracking(lat, lng, heading);
};

// 🚀 ACTIONS
document.getElementById('btnStartDrive').onclick = () => {
    isNavigating = true; isCameraFollow = true;
    document.getElementById('searchContainer').classList.add('hidden');
    document.getElementById('routingCard').classList.add('hidden');
    document.getElementById('navDashboard').classList.remove('hidden');
    map.jumpTo({ center: currentPos, zoom: 19, pitch: 65 });
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
    map.easeTo({ pitch: 45, zoom: 14, bearing: 0 });
}

function clearPOIs() { poiMarkers.forEach(m => m.remove()); poiMarkers = []; }
function debounce(f, w) { let t; return (...a) => { clearTimeout(t); t = setTimeout(() => f(...a), w); }; }
function showCustomToast(msg) { const t = document.getElementById('customToast'); t.innerText = msg; t.classList.add('visible'); setTimeout(() => t.classList.remove('visible'), 3000); }
