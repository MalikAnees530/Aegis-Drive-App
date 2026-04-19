// Aegis Drive - Final "Golden Master" Navigation Logic

const map = new maplibregl.Map({
    container: 'map',
    style: 'https://tiles.openfreemap.org/styles/liberty',
    center: [73.0479, 33.6844], 
    zoom: 14, pitch: 45, antialias: true, attributionControl: false
});

let userMarker = null;
let currentPos = [73.0479, 33.6844];
let isNavigating = false;
let targetPos = null;
let poiMarkers = [];
let currentSpeed = 0;

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

// ---------------------------------------------------------
// PRECISION SEARCH & SUGGESTIONS
// ---------------------------------------------------------

const etSearch = document.getElementById('etSearch');
const suggestionsList = document.getElementById('suggestions');

function optimizeQuery(raw) {
    return raw.toLowerCase()
        .replace(/\bpgc\b/g, "Punjab Group of Colleges")
        .replace(/\bnuml\b/g, "National University of Modern Languages")
        .replace(/\buni\b/g, "University");
}

etSearch.oninput = debounce(async (e) => {
    const rawQ = e.target.value;
    // 🚀 FIXED: Lowered threshold to 1 char for immediate feedback
    if (rawQ.length < 1) { suggestionsList.classList.add('hidden'); return; }
    
    const q = optimizeQuery(rawQ);
    try {
        // High priority Pakistan filter with viewbox bias
        const url = `https://photon.komoot.io/api/?q=${encodeURIComponent(q)}&lat=${currentPos[1]}&lon=${currentPos[0]}&limit=10`;
        const res = await fetch(url);
        const data = await res.json();
        
        suggestionsList.innerHTML = '';
        if (data.features && data.features.length > 0) {
            data.features.forEach(item => {
                const props = item.properties;
                const div = document.createElement('div');
                div.className = 'suggestion-item';
                const name = [props.name, props.district, props.city].filter(Boolean).join(", ");
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
}, 300);

// 🚀 FIXED: Keyboard Search Logic
etSearch.addEventListener('keydown', (e) => {
    if (e.key === 'Enter') {
        performSearch(etSearch.value);
        suggestionsList.classList.add('hidden');
        etSearch.blur();
    }
});

// 🚀 FIXED: Search Icon Logic
document.getElementById('btnSearchSubmit').onclick = () => {
    performSearch(etSearch.value);
    suggestionsList.classList.add('hidden');
    etSearch.blur();
};

async function performSearch(query) {
    if (!query) return;
    showCustomToast("🔍 Pinpointing precise location...");
    const q = optimizeQuery(query);

    try {
        // Deep search with city locking
        const url = `https://nominatim.openstreetmap.org/search?q=${encodeURIComponent(q)}+Pakistan&format=json&limit=1&addressdetails=1`;
        const res = await fetch(url);
        const data = await res.json();
        if (data.length > 0) {
            handleDestinationSelected([parseFloat(data[0].lon), parseFloat(data[0].lat)], data[0].display_name);
        } else {
            showCustomToast("❌ Location details not found.");
        }
    } catch (e) {}
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
    document.getElementById('btnCancelDrive').classList.add('hidden');
    
    planRoute(currentPos, pos, false);
}

// Android Interface
window.updateLocation = function(lat, lng, speedKmh, heading) {
    currentPos = [lng, lat]; currentSpeed = speedKmh;
    if (userMarker) { userMarker.setLngLat(currentPos); if (speedKmh > 1.5) userMarker.setRotation(heading); }
    if (isNavigating) { map.easeTo({ center: currentPos, zoom: 18.5, bearing: heading, duration: 1000 }); updateETADisplay(); }
};

window.onVoiceResult = (text) => { etSearch.value = text; performSearch(text); };

async function planRoute(start, dest, autoStart = false) {
    try {
        const url = `https://router.project-osrm.org/route/v1/driving/${start[0]},${start[1]};${dest[0]},${dest[1]}?overview=full&geometries=geojson`;
        const res = await fetch(url);
        const data = await res.json();
        if (data.routes && data.routes.length > 0) {
            map.getSource('route').setData(data.routes[0].geometry);
            document.getElementById('navDistance').innerText = (data.routes[0].distance / 1000).toFixed(1) + ' km';
            document.getElementById('navTime').innerText = Math.round(data.routes[0].duration / 60) + ' min';
            if (autoStart) startDrive();
        }
    } catch (e) {}
}

function startDrive() {
    isNavigating = true;
    document.getElementById('btnStartDrive').classList.add('hidden');
    document.getElementById('btnCancelDrive').classList.remove('hidden');
    document.getElementById('searchContainer').classList.add('hidden');
    map.flyTo({ center: currentPos, zoom: 19, pitch: 65, bearing: userMarker.getRotation() });
}

function showCustomToast(msg) {
    const toast = document.getElementById('customToast');
    toast.innerText = msg; toast.classList.remove('hidden'); toast.style.opacity = '1';
    setTimeout(() => { toast.style.opacity = '0'; setTimeout(() => toast.classList.add('hidden'), 300); }, 2500);
}

function clearPOIs() { poiMarkers.forEach(m => m.remove()); poiMarkers = []; }
function debounce(func, wait) { let timeout; return function(...args) { clearTimeout(timeout); timeout = setTimeout(() => func.apply(this, args), wait); }; }

document.getElementById('btnVoice').onclick = () => { if (window.Android) window.Android.startVoiceRecognition(); };
document.getElementById('btnRecenter').onclick = () => { if (userMarker) map.flyTo({ center: currentPos, zoom: 18, pitch: 0 }); };
document.getElementById('btnStartDrive').onclick = () => startDrive();
document.getElementById('btnCancelDrive').onclick = () => {
    isNavigating = false; targetPos = null; clearPOIs();
    map.getSource('route').setData({ type: 'Feature', geometry: { type: 'LineString', coordinates: [] } });
    document.getElementById('routingCard').classList.add('hidden');
    document.getElementById('searchContainer').classList.remove('hidden');
    map.easeTo({ pitch: 45, zoom: 14 });
};
