import React, { useState, useEffect, useRef, useMemo } from 'react';
import {
  Map,
  MapControls,
  MapMarker,
  MarkerContent,
  MarkerLabel,
  MarkerPopup,
  MapRoute,
  RouteProgress,
  RouteMarker,
  type MapRef,
  type MapViewport,
} from '@/components/ui/map';
import { Card, CardHeader, CardTitle, CardContent } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Slider } from '@/components/ui/slider';
import {
  MapPin,
  Car,
  Navigation,
  Compass,
  Layers,
  Star,
  Clock,
  ExternalLink,
  LocateFixed,
  Plus,
  Minus,
  Sparkles,
} from 'lucide-react';

const styles = {
  bright: 'https://tiles.openfreemap.org/styles/bright',
  liberty3d: 'https://tiles.openfreemap.org/styles/liberty',
  carto: undefined, // Default Carto basemap
};

type StyleKey = keyof typeof styles;

// Coordinates: VIT Chennai Campus -> Tambaram Railway Station
const start = { name: 'VIT Chennai Campus', lng: 80.1534, lat: 12.8406 };
const end = { name: 'Tambaram Railway Station', lng: 80.1167, lat: 12.9255 };

interface RouteData {
  coordinates: [number, number][];
  duration: number; // seconds
  distance: number; // meters
}

export const MapShowcasePage: React.FC = () => {
  const mapRef = useRef<MapRef>(null);
  const [activeTab, setActiveTab] = useState<'route' | 'controlled' | 'markers' | 'styles'>('route');

  // Styles state
  const [style, setStyle] = useState<StyleKey>('bright');
  const [is3D, setIs3D] = useState(false);

  // Controlled Viewport state
  const [viewport, setViewport] = useState<MapViewport>({
    center: [80.1534, 12.8406],
    zoom: 13,
    bearing: 0,
    pitch: 0,
  });

  // Draggable marker state
  const [draggableMarker, setDraggableMarker] = useState({
    lng: 80.1534,
    lat: 12.8406,
  });

  // OSRM highway route state
  const [route, setRoute] = useState<[number, number][]>([]);
  const [routeDuration, setRouteDuration] = useState<number>(0);
  const [routeDistance, setRouteDistance] = useState<number>(0);
  const [progress, setProgress] = useState(0.35);
  const [isLoading, setIsLoading] = useState(true);
  const [locationStatus, setLocationStatus] = useState<string>('');

  const traveledCoords = useMemo(() => {
    if (route.length < 2) return [];
    const count = Math.max(2, Math.floor(route.length * progress));
    return route.slice(0, count);
  }, [route, progress]);

  const currentCarPoint = useMemo(() => {
    if (traveledCoords.length === 0) return null;
    return traveledCoords[traveledCoords.length - 1];
  }, [traveledCoords]);

  // 3D tilt effect
  useEffect(() => {
    mapRef.current?.easeTo({ pitch: is3D ? 60 : 0, duration: 600 });
  }, [is3D]);

  // Fetch real OSRM turn-by-turn route
  useEffect(() => {
    async function fetchRoutes() {
      try {
        setIsLoading(true);
        const url = `https://router.project-osrm.org/route/v1/driving/${start.lng},${start.lat};${end.lng},${end.lat}?overview=full&geometries=geojson`;
        const res = await fetch(url);
        const data = await res.json();

        if (data.routes?.length > 0) {
          const r = data.routes[0];
          setRoute(r.geometry.coordinates);
          setRouteDuration(r.duration);
          setRouteDistance(r.distance);

          // Fit route
          if (mapRef.current) {
            const coords: [number, number][] = r.geometry.coordinates;
            let minLng = coords[0][0], maxLng = coords[0][0];
            let minLat = coords[0][1], maxLat = coords[0][1];
            coords.forEach(([lng, lat]) => {
              if (lng < minLng) minLng = lng;
              if (lng > maxLng) maxLng = lng;
              if (lat < minLat) minLat = lat;
              if (lat > maxLat) maxLat = lat;
            });
            mapRef.current.fitBounds(
              [[minLng, minLat], [maxLng, maxLat]],
              { padding: { top: 60, bottom: 60, left: 40, right: 40 }, duration: 800 }
            );
          }
        }
      } catch (err) {
        console.error('Failed to fetch OSRM route:', err);
      } finally {
        setIsLoading(false);
      }
    }

    fetchRoutes();
  }, []);

  // Zoom In / Zoom Out handlers
  const handleZoomIn = () => {
    if (mapRef.current) {
      mapRef.current.zoomTo(mapRef.current.getZoom() + 1, { duration: 300 });
    }
  };

  const handleZoomOut = () => {
    if (mapRef.current) {
      mapRef.current.zoomTo(mapRef.current.getZoom() - 1, { duration: 300 });
    }
  };

  // GPS Locate (PC & iPhone)
  const handleLocateMe = () => {
    if (!('geolocation' in navigator)) {
      setLocationStatus('Geolocation not supported by this browser');
      return;
    }
    setLocationStatus('Detecting GPS location...');
    navigator.geolocation.getCurrentPosition(
      (pos) => {
        const { longitude, latitude } = pos.coords;
        setLocationStatus(`Located: ${latitude.toFixed(4)}, ${longitude.toFixed(4)}`);
        if (mapRef.current) {
          mapRef.current.flyTo({
            center: [longitude, latitude],
            zoom: 15,
            duration: 1200,
          });
        }
      },
      (err) => {
        setLocationStatus(`GPS unavailable (${err.message}). Centering on VIT Chennai`);
        if (mapRef.current) {
          mapRef.current.flyTo({ center: [80.1534, 12.8406], zoom: 14, duration: 1000 });
        }
      },
      { enableHighAccuracy: true, timeout: 8000 }
    );
  };

  const selectedStyleUrl = styles[style];

  return (
    <div className="flex flex-col h-[calc(100vh-60px)] w-full overflow-hidden bg-slate-950 text-slate-100">
      {/* Top Header & Tab Switcher */}
      <div className="flex items-center justify-between border-b border-slate-800 bg-slate-900 px-3 py-2 gap-2 z-20 shrink-0">
        <div className="flex items-center gap-2 shrink-0">
          <div className="flex h-7 w-7 items-center justify-center rounded-lg bg-amber-500 font-black text-black text-xs">
            M
          </div>
          <div>
            <h1 className="text-xs sm:text-sm font-bold tracking-tight text-white flex items-center gap-1.5">
              MapCN <span className="text-[9px] bg-amber-500/20 text-amber-400 border border-amber-500/30 px-1 py-0.2 rounded font-mono">mapcn.dev</span>
            </h1>
            <p className="text-[10px] text-slate-400 hidden sm:block">MapLibre GL + OpenFreeMap + OSRM Highway Routing</p>
          </div>
        </div>

        {/* Mode Selector Tabs */}
        <div className="flex items-center overflow-x-auto rounded-lg bg-slate-800 p-0.5 text-[11px] font-medium shrink-0">
          <button
            onClick={() => setActiveTab('route')}
            className={`rounded-md px-2.5 py-1 transition-colors whitespace-nowrap ${
              activeTab === 'route' ? 'bg-amber-500 text-black font-bold shadow' : 'text-slate-300 hover:text-white'
            }`}
          >
            OSRM Route
          </button>
          <button
            onClick={() => setActiveTab('controlled')}
            className={`rounded-md px-2.5 py-1 transition-colors whitespace-nowrap ${
              activeTab === 'controlled' ? 'bg-amber-500 text-black font-bold shadow' : 'text-slate-300 hover:text-white'
            }`}
          >
            Controlled
          </button>
          <button
            onClick={() => setActiveTab('markers')}
            className={`rounded-md px-2.5 py-1 transition-colors whitespace-nowrap ${
              activeTab === 'markers' ? 'bg-amber-500 text-black font-bold shadow' : 'text-slate-300 hover:text-white'
            }`}
          >
            Markers
          </button>
        </div>
      </div>

      {/* Main Map View Area */}
      <div className="relative flex-1 w-full h-full">
        <Map
          ref={mapRef}
          center={viewport.center}
          zoom={viewport.zoom}
          onViewportChange={setViewport}
          styles={
            selectedStyleUrl
              ? { light: selectedStyleUrl, dark: selectedStyleUrl }
              : undefined
          }
          loading={isLoading}
          className="h-full w-full"
        >
          {/* MapCN Standard Controls */}
          <MapControls
            position="bottom-right"
            showZoom={true}
            showCompass={true}
            showLocate={true}
            showFullscreen={true}
            onLocate={(coords) => {
              setLocationStatus(`Located: ${coords.latitude.toFixed(4)}, ${coords.longitude.toFixed(4)}`);
            }}
          />

          {/* 1. OSRM Highway Route (Full Path in Slate) */}
          {activeTab === 'route' && route.length > 0 && (
            <MapRoute
              id="base-route"
              coordinates={route}
              color="#64748b"
              width={5}
              opacity={0.6}
            />
          )}

          {/* Traveled Path in vibrant Rapido Amber */}
          {activeTab === 'route' && traveledCoords.length > 0 && (
            <MapRoute
              id="traveled-route"
              coordinates={traveledCoords}
              color="#f59e0b"
              width={6}
              opacity={1}
            />
          )}

          {/* Start Marker */}
          {activeTab === 'route' && (
            <MapMarker longitude={start.lng} latitude={start.lat}>
              <MarkerContent>
                <div className="flex h-5 w-5 items-center justify-center rounded-full border-2 border-white bg-emerald-500 shadow-md">
                  <div className="h-2 w-2 rounded-full bg-white" />
                </div>
              </MarkerContent>
              <MarkerLabel position="bottom">
                <span className="bg-slate-900/90 text-emerald-400 font-bold px-1.5 py-0.5 rounded text-[10px] border border-emerald-500/30">
                  🟢 {start.name}
                </span>
              </MarkerLabel>
            </MapMarker>
          )}

          {/* Live Moving Captain Marker */}
          {activeTab === 'route' && currentCarPoint && (
            <MapMarker longitude={currentCarPoint[0]} latitude={currentCarPoint[1]}>
              <MarkerContent>
                <div className="flex h-8 w-8 items-center justify-center rounded-full border-2 border-amber-300 bg-amber-500 shadow-xl ring-4 ring-amber-400/30">
                  <Car className="h-4 w-4 text-black" />
                </div>
              </MarkerContent>
              <MarkerLabel position="top">
                <span className="bg-amber-500 text-black font-black px-1.5 py-0.5 rounded text-[10px] shadow">
                  Captain Live ({Math.round(progress * 100)}%)
                </span>
              </MarkerLabel>
            </MapMarker>
          )}

          {/* Destination Marker */}
          {activeTab === 'route' && (
            <MapMarker longitude={end.lng} latitude={end.lat}>
              <MarkerContent>
                <div className="flex h-5 w-5 items-center justify-center rounded-full border-2 border-white bg-rose-500 shadow-md">
                  <div className="h-2 w-2 rounded-full bg-white" />
                </div>
              </MarkerContent>
              <MarkerLabel position="bottom">
                <span className="bg-slate-900/90 text-rose-400 font-bold px-1.5 py-0.5 rounded text-[10px] border border-rose-500/30">
                  🔴 {end.name}
                </span>
              </MarkerLabel>
            </MapMarker>
          )}

          {/* 2. Draggable Marker Demo */}
          {activeTab === 'markers' && (
            <MapMarker
              draggable
              longitude={draggableMarker.lng}
              latitude={draggableMarker.lat}
              onDrag={(lngLat) => setDraggableMarker({ lng: lngLat.lng, lat: lngLat.lat })}
            >
              <MarkerContent>
                <div className="cursor-move animate-bounce">
                  <MapPin className="fill-amber-500 stroke-black text-amber-500" size={36} />
                </div>
              </MarkerContent>
              <MarkerPopup>
                <div className="space-y-1 p-2 text-xs text-slate-800 dark:text-slate-100">
                  <p className="font-bold text-amber-600">Draggable Coordinate Pin</p>
                  <p className="font-mono text-[11px] text-slate-600 dark:text-slate-400">
                    Lat: {draggableMarker.lat.toFixed(4)}, Lng: {draggableMarker.lng.toFixed(4)}
                  </p>
                  <p className="text-[10px] text-slate-500">Drag me anywhere on the map!</p>
                </div>
              </MarkerPopup>
            </MapMarker>
          )}

          {/* 3. Landmark Popups Demo */}
          {activeTab === 'markers' && (
            <>
              <MapMarker longitude={80.1167} latitude={12.9255}>
                <MarkerContent>
                  <div className="flex h-6 w-6 cursor-pointer items-center justify-center rounded-full border-2 border-white bg-rose-500 shadow-lg hover:scale-110">
                    <Navigation className="h-3.5 w-3.5 text-white" />
                  </div>
                </MarkerContent>
                <MarkerPopup className="w-56 p-0">
                  <div className="p-3 text-xs space-y-1.5 text-slate-900 dark:text-slate-100">
                    <p className="text-[10px] uppercase font-bold text-rose-600">Transit Hub</p>
                    <h4 className="font-bold text-sm">Tambaram Railway Station</h4>
                    <div className="flex items-center gap-1 text-amber-500 font-semibold text-xs">
                      <Star className="h-3.5 w-3.5 fill-amber-400" />
                      <span>4.8</span>
                      <span className="text-slate-400 text-[10px]">(8,234 reviews)</span>
                    </div>
                    <div className="flex items-center gap-1 text-slate-500 text-[11px]">
                      <Clock className="h-3 w-3" />
                      <span>Open 24 Hours</span>
                    </div>
                  </div>
                </MarkerPopup>
              </MapMarker>
            </>
          )}
        </Map>

        {/* Floating Quick Action Overlay Pill */}
        <div className="absolute top-4 right-4 z-30 pointer-events-auto flex items-center gap-1.5 rounded-xl border border-slate-700/80 bg-slate-900/95 p-1 shadow-xl backdrop-blur">
          {/* Style Selector */}
          <select
            value={style}
            onChange={(e) => setStyle(e.target.value as StyleKey)}
            className="rounded-lg border border-slate-700 bg-slate-800 px-2.5 py-1 text-xs font-medium text-slate-200 focus:outline-none"
          >
            <option value="bright">OpenFreeMap Bright</option>
            <option value="liberty3d">OpenFreeMap 3D</option>
            <option value="carto">Carto Positron</option>
          </select>

          {/* 3D Pitch Toggle */}
          <button
            type="button"
            onClick={() => setIs3D(!is3D)}
            className={`flex h-7 px-2.5 items-center justify-center rounded-lg text-xs font-bold transition-colors ${
              is3D ? 'bg-amber-500 text-black shadow' : 'text-slate-300 hover:bg-slate-800'
            }`}
            title="Toggle 3D Pitch Tilt"
          >
            3D
          </button>

          <div className="h-4 w-px bg-slate-700" />

          {/* Zoom In Button (+) */}
          <button
            type="button"
            onClick={handleZoomIn}
            className="flex h-7 w-7 items-center justify-center rounded-lg text-slate-300 hover:bg-slate-800 active:scale-95"
            title="Zoom In"
          >
            <Plus className="h-4 w-4" />
          </button>

          {/* Zoom Out Button (-) */}
          <button
            type="button"
            onClick={handleZoomOut}
            className="flex h-7 w-7 items-center justify-center rounded-lg text-slate-300 hover:bg-slate-800 active:scale-95"
            title="Zoom Out"
          >
            <Minus className="h-4 w-4" />
          </button>

          {/* Locate Me Button */}
          <button
            type="button"
            onClick={handleLocateMe}
            className="flex h-7 w-7 items-center justify-center rounded-lg text-slate-300 hover:bg-slate-800 active:scale-95"
            title="Detect GPS Location"
          >
            <LocateFixed className="h-4 w-4 text-emerald-400" />
          </button>
        </div>

        {/* Tab 1: Route Scrubbing Drawer */}
        {activeTab === 'route' && (
          <div className="absolute bottom-4 left-3 right-3 sm:right-auto sm:w-72 z-30 pointer-events-auto rounded-2xl border border-slate-800 bg-slate-900/95 p-3 shadow-2xl backdrop-blur">
            <div className="mb-2 flex items-center justify-between text-xs">
              <span className="font-bold text-white flex items-center gap-1.5">
                <Sparkles className="h-3.5 w-3.5 text-amber-400" />
                Live Driver Progress
              </span>
              <span className="font-mono text-amber-400 font-bold">
                {Math.round(progress * 100)}%
              </span>
            </div>
            <Slider
              value={[progress]}
              onValueChange={([val]) => setProgress(val)}
              min={0}
              max={1}
              step={0.01}
            />
            <div className="mt-2.5 flex items-center justify-between text-[11px] text-slate-400 border-t border-slate-800 pt-2">
              <span>{Math.round(routeDuration / 60)} min</span>
              <span>{(routeDistance / 1000).toFixed(1)} km highway</span>
            </div>
          </div>
        )}

        {/* Tab 2: Controlled Viewport Telemetry Box */}
        {activeTab === 'controlled' && (
          <div className="absolute top-4 left-4 z-30 pointer-events-auto flex flex-wrap gap-x-3 gap-y-1 rounded-xl border border-slate-800 bg-slate-900/90 px-3 py-2 font-mono text-xs shadow-lg backdrop-blur">
            <span>
              <span className="text-slate-500">lng:</span>{' '}
              <span className="text-amber-400">{viewport.center[0].toFixed(4)}</span>
            </span>
            <span>
              <span className="text-slate-500">lat:</span>{' '}
              <span className="text-amber-400">{viewport.center[1].toFixed(4)}</span>
            </span>
            <span>
              <span className="text-slate-500">zoom:</span>{' '}
              <span className="text-emerald-400">{viewport.zoom.toFixed(1)}</span>
            </span>
            <span>
              <span className="text-slate-500">bearing:</span>{' '}
              <span className="text-sky-400">{viewport.bearing.toFixed(1)}°</span>
            </span>
            <span>
              <span className="text-slate-500">pitch:</span>{' '}
              <span className="text-purple-400">{viewport.pitch.toFixed(1)}°</span>
            </span>
          </div>
        )}

        {/* Geolocation Status Toast / Badge */}
        {locationStatus && (
          <div className="absolute bottom-4 left-1/2 -translate-x-1/2 z-30 pointer-events-auto rounded-full bg-emerald-500/90 text-black px-4 py-1.5 text-xs font-bold shadow-lg">
            {locationStatus}
          </div>
        )}
      </div>
    </div>
  );
};

export default MapShowcasePage;
