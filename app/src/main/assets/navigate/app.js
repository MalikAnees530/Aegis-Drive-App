// Aegis Drive - Final "Golden Master" Navigation Logic

const map = new maplibregl.Map({
    container: 'map',
    style: 'https://tiles.openfreemap.org/styles/liberty',
    center: [73.0479, 33.6844], 
    zoom: 14, pitch: 45, antialias: true, attributionControl: false
});

map.on('style.load', () => {
    const layers = map.getStyle().layers;
    layers.forEach(layer => {
        if (layer.type === 'symbol') {
            map.setLayoutProperty(layer.id, 'visibility', 'visible');
            map.setLayoutProperty(layer.id, 'text-field', ['get', 'name:en']);
        }
    });
});

let userMarker = null;
let currentPos = [73.0479, 33.6844];
let isNavigating = false;
let isEditMode = false;
let targetPos = null;
let poiMarkers = [];

const arrowEl = document.createElement('div');
arrowEl.style.width = '24px'; arrowEl.style.height = '24px';
arrowEl.style.background = 'url(\'data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="%2338BDF8"><path d="M12 2L4.5 20.29l.71.71L12 18l6.79 3 .71-.71z"/></svg>\')';
arrowEl.style.backgroundSize = 'contain';

map.on('load', () => {
    userMarker = new maplibregl.Marker({ element: arrowEl, rotationAlignment: 'map' }).setLngLat(currentPos).addTo(map);
    map.addSource('route', { type: 'geojson', data: { type: 'Feature', geometry: { type: 'LineString', coordinates: [] } } });
    map.addLayer({ id: 'route', type: 'line', source: 'route', layout: { 'line-join': 'round', 'line-cap': 'round' },
        paint: { 'line-color': '#38BDF8', 'line-width': 10, 'line-opacity': 0.85 } });
    map.addControl(new maplibregl.NavigationControl({ showCompass: false }), 'bottom-left');
});

// 🚀 DOUBLE-CLICK TO SELECT DESTINATION (GITHUB LOGIC)
map.on('dblclick', (e) => {
    if (isEditMode) return; // Don't conflict with Home editing
    const pos = [e.lngLat.lng, e.lngLat.lat];
    handleDestinationSelected(pos, "Selected Location");
});

// ---------------------------------------------------------
// 🏠 HOME & EDIT MODE LOGIC
// ---------------------------------------------------------

const btnHome = document.getElementById('btnHome');
const homeMenu = document.getElementById('homeMenu');
const editOverlay = document.getElementById('editOverlay');

btnHome.onclick = (e) => {
    e.stopPropagation();
    if (isNavigating) return;
    homeMenu.classList.toggle('hidden');
};

document.addEventListener('click', () => homeMenu.classList.add('hidden'));

document.getElementById('menuEditHome').onclick = () => {
    isEditMode = true;
    editOverlay.classList.remove('hidden');
    showCustomToast("📍 Click anywhere on the map to set Home");
};

document.getElementById('menuNavigateHome').onclick = () => {
    if (window.Android) window.Android.triggerNavigateHome();
};

document.getElementById('btnCancelEdit').onclick = () => {
    isEditMode = false;
    editOverlay.classList.add('hidden');
};

map.on('click', async (e) => {
    if (!isEditMode) return;
    isEditMode = false;
    editOverlay.classList.add('hidden');
    
    if (window.Android) {
        window.Android.saveHomeLocation(e.lngLat.lat, e.lngLat.lng);
        showCustomToast("🏠 Home Location Saved Successfully");
        clearPOIs();
        const homeLabel = document.createElement('div');
        homeLabel.className = 'marker-bubble';
        homeLabel.style.borderColor = '#10B981';
        homeLabel.innerText = "🏠 My Home";
        const m = new maplibregl.Marker({ element: homeLabel }).setLngLat(e.lngLat).addTo(map);
        poiMarkers.push(m);
        
        // Logic fix: Immediately prepare navigation for the NEW home
        handleDestinationSelected([e.lngLat.lng, e.lngLat.lat], "Home");
    }
});

// ---------------------------------------------------------
// PRECISION SEARCH & INTERACTION
// ---------------------------------------------------------

const etSearch = document.getElementById('etSearch');
const suggestionsList = document.getElementById('suggestions');

function optimizeQuery(raw) {
    let q = raw.toLowerCase()
        .replace(/\bfast university\b/g, "fast university h11 islamabad")
        .replace(/\bpgc\b/g, "punjab group of colleges blue area")
        .replace(/\bnuml\b/g, "national university of modern languages h9 islamabad")
        .replace(/\baps\b/g, "army public school islamabad");

    // 🚀 FIXED: Aggressive Islamabad Sector Normalization for Voice/Keyboard
    // 1. First, collapse spaces/dashes (e.g., "g 9 2" -> "g92")
    q = q.replace(/\b([a-i])[\s\/-]+(\d)\s*(\d?)\b/g, "$1$2$3");

    // 2. Intelligent splitting and formatting (e.g., "g92" -> "G-9/2 Islamabad")
    q = q.replace(/\b([a-i])(\d{1,3})\b/g, (match, letter, digits) => {
        let sector = "";
        let subsector = "";
        
        if (digits.length === 1) {
            sector = digits;
        } else if (digits.length === 2) {
            // Islamabad rule: sectors 5-9 have subsectors (e.g., 92 is 9/2)
            if (digits[0] >= '5' && digits[0] <= '9') {
                sector = digits[0];
                subsector = digits[1];
            } else {
                sector = digits;
            }
        } else if (digits.length === 3) {
            sector = digits.substring(0, 2);
            subsector = digits[2];
        }

        let res = `${letter.toUpperCase()}-${sector}`;
        if (subsector) res += `/${subsector}`;
        return res + " Islamabad"; 
    });

    return q;
}

etSearch.oninput = debounce(async (e) => {
    fetchSuggestions(e.target.value);
}, 300);

async function fetchSuggestions(query) {
    if (query.length < 1) { suggestionsList.classList.add('hidden'); return; }
    const q = optimizeQuery(query);
    try {
        const url = `https://photon.komoot.io/api/?q=${encodeURIComponent(q)}&lat=${currentPos[1]}&lon=${currentPos[0]}&limit=20`;
        const res = await fetch(url);
        const data = await res.json();
        suggestionsList.innerHTML = '';
        if (data.features && data.features.length > 0) {
            data.features.forEach(item => {
                const props = item.properties;
                const div = document.createElement('div');
                div.className = 'suggestion-item';
                const parts = [props.name, props.street, props.district, props.city].filter(Boolean);
                const name = parts.join(", ");
                div.innerText = name;
                div.onclick = () => { 
                    etSearch.value = name; 
                    suggestionsList.classList.add('hidden'); 
                    handleDestinationSelected(item.geometry.coordinates, name); 
                };
                suggestionsList.appendChild(div);
            });
            suggestionsList.classList.remove('hidden');
        } else {
            suggestionsList.classList.add('hidden');
        }
    } catch (e) {}
}

etSearch.addEventListener('keydown', (e) => { 
    if (e.key === 'Enter') {
        performSearch(etSearch.value);
        suggestionsList.classList.add('hidden');
        etSearch.blur();
    }
});

document.getElementById('btnSearchSubmit').onclick = () => {
    performSearch(etSearch.value);
    suggestionsList.classList.add('hidden');
    etSearch.blur();
};

async function performSearch(query) {
    if (!query) return;
    showCustomToast("🔍 Pinpointing...");
    const q = optimizeQuery(query);
    try {
        // Switch to Photon for precise search - better handling of Islamabad sectors
        const url = `https://photon.komoot.io/api/?q=${encodeURIComponent(q)}&lat=${currentPos[1]}&lon=${currentPos[0]}&limit=1`;
        const res = await fetch(url);
        const data = await res.json();
        
        if (data.features && data.features.length > 0) {
            const item = data.features[0];
            const props = item.properties;
            const name = [props.name, props.district, props.city].filter(Boolean).join(", ");
            handleDestinationSelected(item.geometry.coordinates, name);
        } else {
            showCustomToast("❌ Location not found");
        }
    } catch (e) { 
        showCustomToast("❌ Search failed"); 
    }
}

function handleDestinationSelected(pos, name) {
    targetPos = pos;
    poiMarkers.forEach(m => m.remove()); poiMarkers = [];
    const label = document.createElement('div');
    label.className = 'marker-bubble';
    label.innerText = `📍 ${name.split(',')[0]}`;
    const m = new maplibregl.Marker({ element: label }).setLngLat(pos).addTo(map);
    poiMarkers.push(m);
    map.flyTo({ center: pos, zoom: 17, pitch: 0 });
    document.getElementById('navStreet').innerText = name.split(',')[0];
    document.getElementById('routingCard').classList.remove('hidden');
    document.getElementById('btnStartDrive').classList.remove('hidden');
    document.getElementById('btnCancelDrive').classList.remove('hidden'); // 🚀 FIXED: Always show STOP button when card is up
    
    // UI Fix: Show user it's calculating
    document.getElementById('navTime').innerText = "Calculating...";
    document.getElementById('navDistance').innerText = "-- km";
    
    planRoute(currentPos, pos);
}

// ---------------------------------------------------------
// ROUTING & HANDSHAKE
// ---------------------------------------------------------

window.updateLocation = function(lat, lng, speed, heading) {
    currentPos = [lng, lat];
    if (userMarker) {
        userMarker.setLngLat(currentPos);
        if (speed > 1.5) userMarker.setRotation(heading);
    }
    if (isNavigating) {
        map.easeTo({ center: currentPos, zoom: 18.5, bearing: heading, duration: 1000 });
        updateETADisplay();
    }
};

window.onVoiceResult = (text) => { etSearch.value = text; performSearch(text); };

async function planRoute(start, dest) {
    try {
        const url = `https://router.project-osrm.org/route/v1/driving/${start[0]},${start[1]};${dest[0]},${dest[1]}?overview=full&geometries=geojson`;
        const res = await fetch(url);
        const data = await res.json();
        if (data.routes && data.routes.length > 0) {
            map.getSource('route').setData(data.routes[0].geometry);
            document.getElementById('navDistance').innerText = (data.routes[0].distance / 1000).toFixed(1) + ' km';
            document.getElementById('navTime').innerText = Math.round(data.routes[0].duration / 60) + ' min';
        }
    } catch (e) {}
}

document.getElementById('btnStartDrive').onclick = () => {
    isNavigating = true; 
    document.getElementById('searchContainer').classList.add('hidden');
    document.getElementById('btnStartDrive').classList.add('hidden');
    document.getElementById('btnCancelDrive').classList.remove('hidden');
    map.flyTo({ center: currentPos, zoom: 19, pitch: 65, bearing: userMarker.getRotation() });
};

document.getElementById('btnCancelDrive').onclick = () => {
    isNavigating = false; targetPos = null;
    poiMarkers.forEach(m => m.remove()); poiMarkers = [];
    map.getSource('route').setData({ type: 'Feature', geometry: { type: 'LineString', coordinates: [] } });
    document.getElementById('routingCard').classList.add('hidden');
    document.getElementById('searchContainer').classList.remove('hidden');
    document.getElementById('btnStartDrive').classList.remove('hidden');
    map.easeTo({ pitch: 45, zoom: 14 });
};

document.getElementById('btnRecenter').onclick = () => map.flyTo({ center: currentPos, zoom: 18, pitch: 0 });
document.getElementById('btnVoice').onclick = () => { if (window.Android) window.Android.startVoiceRecognition(); };

function showCustomToast(msg) {
    const t = document.getElementById('customToast'); t.innerText = msg;
    t.classList.remove('hidden'); t.style.opacity = '1';
    setTimeout(() => { t.style.opacity = '0'; setTimeout(() => t.classList.add('hidden'), 300); }, 2500);
}

function clearPOIs() { poiMarkers.forEach(m => m.remove()); poiMarkers = []; }
function debounce(f, w) { let t; return (...a) => { clearTimeout(t); t = setTimeout(() => f(...a), w); }; }
