import React, { useEffect, useState, useRef, useMemo, useCallback } from 'react';
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
import {
  Navigation,
  Compass,
  Layers,
  MapPin,
  Car,
  Bike,
  Plus,
  Minus,
  Maximize2,
  LocateFixed,
  Sparkles,
} from 'lucide-react';

export interface NearbyVehicle {
  id: string | number;
  lat: number;
  lng: number;
  type: 'bike' | 'scooty' | 'auto' | 'cab';
  rotation?: number;
}

interface MapProps {
  pickupLat?: number;
  pickupLng?: number;
  pickupName?: string;
  destLat?: number;
  destLng?: number;
  destName?: string;
  driverLat?: number;
  driverLng?: number;
  nearbyVehicles?: NearbyVehicle[];
  showNearbyCluster?: boolean;
  height?: number | string;
  onLocationSelect?: (lat: number, lng: number) => void;
}

const mapStyles = {
  carto: undefined, // Default Carto basemap
  bright: 'https://tiles.openfreemap.org/styles/bright',
  liberty3d: 'https://tiles.openfreemap.org/styles/liberty',
};

type StyleKey = keyof typeof mapStyles;

export const RideMap: React.FC<MapProps> = ({
  pickupLat,
  pickupLng,
  pickupName = 'Pickup Location',
  destLat,
  destLng,
  destName = 'Destination',
  driverLat,
  driverLng,
  nearbyVehicles,
  showNearbyCluster = true,
  height = 420,
  onLocationSelect,
}) => {
  const mapRef = useRef<MapRef>(null);

  // Default coordinate: VIT Chennai Campus
  const defaultLng = 80.1534;
  const defaultLat = 12.8406;

  const currentCenterLng = pickupLng ?? defaultLng;
  const currentCenterLat = pickupLat ?? defaultLat;

  const [styleKey, setStyleKey] = useState<StyleKey>('bright');
  const [is3D, setIs3D] = useState(false);
  const [routeCoords, setRouteCoords] = useState<[number, number][]>([]);
  const [altRouteCoords, setAltRouteCoords] = useState<[number, number][]>([]);
  const [etaText, setEtaText] = useState<string | null>(null);
  const [distanceText, setDistanceText] = useState<string | null>(null);
  const [isLoadingRoute, setIsLoadingRoute] = useState(false);
  const [currentZoom, setCurrentZoom] = useState(13);

  // Controlled viewport state for seamless responsiveness
  const [viewport, setViewport] = useState<MapViewport>({
    center: [currentCenterLng, currentCenterLat],
    zoom: 13,
    bearing: 0,
    pitch: 0,
  });

  // Simulated nearby vehicle cluster around pickup
  const defaultNearbyCluster: NearbyVehicle[] = useMemo(() => {
    const lat = pickupLat ?? defaultLat;
    const lng = pickupLng ?? defaultLng;
    return [
      { id: 'v1', lat: lat + 0.0022, lng: lng - 0.0018, type: 'auto', rotation: 42 },
      { id: 'v2', lat: lat - 0.0019, lng: lng + 0.0021, type: 'bike', rotation: 120 },
      { id: 'v3', lat: lat + 0.0031, lng: lng + 0.0015, type: 'cab', rotation: 220 },
      { id: 'v4', lat: lat - 0.0014, lng: lng - 0.0028, type: 'scooty', rotation: 85 },
      { id: 'v5', lat: lat + 0.0012, lng: lng + 0.0032, type: 'auto', rotation: 310 },
    ];
  }, [pickupLat, pickupLng, defaultLat, defaultLng]);

  const activeVehicles = nearbyVehicles ?? (showNearbyCluster ? defaultNearbyCluster : []);

  // Fetch real OSRM driving route when pickup and destination are provided
  useEffect(() => {
    if (!pickupLat || !pickupLng || !destLat || !destLng) {
      setRouteCoords([]);
      setAltRouteCoords([]);
      setEtaText(null);
      setDistanceText(null);
      return;
    }

    const controller = new AbortController();
    setIsLoadingRoute(true);

    const fetchOSRMRoute = async () => {
      try {
        const url = `https://router.project-osrm.org/route/v1/driving/${pickupLng},${pickupLat};${destLng},${destLat}?overview=full&geometries=geojson&alternatives=true`;
        const res = await fetch(url, { signal: controller.signal });
        if (!res.ok) throw new Error('OSRM non-200');

        const data = await res.json();
        if (data.code === 'Ok' && data.routes?.length > 0) {
          const primary = data.routes[0];
          // OSRM coordinates are [longitude, latitude]
          const coords: [number, number][] = primary.geometry.coordinates;
          setRouteCoords(coords);

          const mins = Math.round(primary.duration / 60);
          const distKm = (primary.distance / 1000).toFixed(1);
          setEtaText(`${mins} mins`);
          setDistanceText(`${distKm} km`);

          // Alternative route if present
          if (data.routes.length > 1) {
            setAltRouteCoords(data.routes[1].geometry.coordinates);
          } else {
            setAltRouteCoords([]);
          }

          // Fit bounds to fit the route on the map
          if (mapRef.current && coords.length > 0) {
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
              { padding: { top: 60, bottom: 60, left: 50, right: 50 }, duration: 800 }
            );
          }
        } else {
          // Fallback to straight line
          setRouteCoords([[pickupLng, pickupLat], [destLng, destLat]]);
        }
      } catch (err: any) {
        if (err.name !== 'AbortError') {
          setRouteCoords([[pickupLng, pickupLat], [destLng, destLat]]);
        }
      } finally {
        setIsLoadingRoute(false);
      }
    };

    fetchOSRMRoute();
    return () => controller.abort();
  }, [pickupLat, pickupLng, destLat, destLng]);

  // Handle 3D / 2D pitch toggle
  const toggle3D = useCallback(() => {
    const nextIs3D = !is3D;
    setIs3D(nextIs3D);
    if (mapRef.current) {
      mapRef.current.easeTo({
        pitch: nextIs3D ? 60 : 0,
        bearing: nextIs3D ? -20 : 0,
        duration: 600,
      });
    }
  }, [is3D]);

  // Zoom In button handler
  const handleZoomIn = useCallback(() => {
    if (mapRef.current) {
      const z = mapRef.current.getZoom();
      mapRef.current.zoomTo(z + 1, { duration: 250 });
      setCurrentZoom(z + 1);
    }
  }, []);

  // Zoom Out button handler
  const handleZoomOut = useCallback(() => {
    if (mapRef.current) {
      const z = mapRef.current.getZoom();
      mapRef.current.zoomTo(z - 1, { duration: 250 });
      setCurrentZoom(z - 1);
    }
  }, []);

  // Locate current GPS position (works on iPhone, Android, and PC)
  const handleLocateMe = useCallback(() => {
    if (!('geolocation' in navigator)) {
      // Graceful fallback to default campus
      if (mapRef.current) {
        mapRef.current.flyTo({ center: [defaultLng, defaultLat], zoom: 14, duration: 1000 });
      }
      return;
    }

    navigator.geolocation.getCurrentPosition(
      (pos) => {
        const { longitude, latitude } = pos.coords;
        if (mapRef.current) {
          mapRef.current.flyTo({
            center: [longitude, latitude],
            zoom: 15,
            duration: 1200,
          });
        }
        onLocationSelect?.(latitude, longitude);
      },
      (err) => {
        console.warn('Geolocation unavailable or denied, centering on default', err);
        if (mapRef.current) {
          mapRef.current.flyTo({ center: [defaultLng, defaultLat], zoom: 14, duration: 1000 });
        }
      },
      { timeout: 8000, enableHighAccuracy: true }
    );
  }, [defaultLng, defaultLat, onLocationSelect]);

  const selectedStyleUrl = mapStyles[styleKey];

  return (
    <div
      className="relative w-full overflow-hidden rounded-2xl border border-gray-200/80 bg-slate-900/10 shadow-lg dark:border-gray-800"
      style={{ height }}
    >
      {/* 1. Core MapCN Map Component */}
      <Map
        ref={mapRef}
        center={[currentCenterLng, currentCenterLat]}
        zoom={currentZoom}
        styles={
          selectedStyleUrl
            ? { light: selectedStyleUrl, dark: selectedStyleUrl }
            : undefined
        }
        loading={isLoadingRoute}
        className="h-full w-full"
      >
        {/* Built-in MapCN Controls */}
        <MapControls
          position="bottom-right"
          showZoom={true}
          showCompass={true}
          showLocate={true}
          showFullscreen={true}
          onLocate={(coords) => onLocationSelect?.(coords.latitude, coords.longitude)}
        />

        {/* Alternative OSRM Route (subtle slate line) */}
        {altRouteCoords.length > 0 && (
          <MapRoute
            coordinates={altRouteCoords}
            color="#94a3b8"
            width={4}
            opacity={0.65}
            dashArray={[1, 1.5]}
          />
        )}

        {/* Primary OSRM Highway Route (Rapido Amber/Blue) */}
        {routeCoords.length > 0 && (
          <MapRoute
            coordinates={routeCoords}
            color="#f59e0b"
            width={6}
            opacity={0.95}
          />
        )}

        {/* Pickup Marker */}
        {pickupLat && pickupLng && (
          <MapMarker longitude={pickupLng} latitude={pickupLat}>
            <MarkerContent>
              <div className="group relative flex cursor-pointer flex-col items-center">
                <div className="flex h-7 w-7 items-center justify-center rounded-full border-2 border-white bg-emerald-500 shadow-lg transition-transform group-hover:scale-110">
                  <MapPin className="h-4 w-4 text-white" />
                </div>
                <div className="absolute -bottom-1 h-2 w-2 rotate-45 bg-emerald-600" />
              </div>
            </MarkerContent>
            <MarkerLabel position="bottom">
              <span className="rounded bg-white/95 px-1.5 py-0.5 text-[10px] font-bold text-gray-800 shadow border border-gray-200">
                🟢 {pickupName}
              </span>
            </MarkerLabel>
            <MarkerPopup>
              <div className="p-2 text-xs">
                <p className="font-bold text-emerald-600">Pickup Location</p>
                <p className="text-gray-700">{pickupName}</p>
                <p className="mt-1 font-mono text-[10px] text-gray-500">
                  {pickupLat.toFixed(4)}, {pickupLng.toFixed(4)}
                </p>
              </div>
            </MarkerPopup>
          </MapMarker>
        )}

        {/* Destination Marker */}
        {destLat && destLng && (
          <MapMarker longitude={destLng} latitude={destLat}>
            <MarkerContent>
              <div className="group relative flex cursor-pointer flex-col items-center">
                <div className="flex h-7 w-7 items-center justify-center rounded-full border-2 border-white bg-rose-500 shadow-lg transition-transform group-hover:scale-110">
                  <Navigation className="h-4 w-4 text-white" />
                </div>
                <div className="absolute -bottom-1 h-2 w-2 rotate-45 bg-rose-600" />
              </div>
            </MarkerContent>
            <MarkerLabel position="bottom">
              <span className="rounded bg-white/95 px-1.5 py-0.5 text-[10px] font-bold text-gray-800 shadow border border-gray-200">
                🔴 {destName}
              </span>
            </MarkerLabel>
            <MarkerPopup>
              <div className="p-2 text-xs">
                <p className="font-bold text-rose-600">Destination</p>
                <p className="text-gray-700">{destName}</p>
                <p className="mt-1 font-mono text-[10px] text-gray-500">
                  {destLat.toFixed(4)}, {destLng.toFixed(4)}
                </p>
              </div>
            </MarkerPopup>
          </MapMarker>
        )}

        {/* Live Driver Marker */}
        {driverLat && driverLng && (
          <MapMarker longitude={driverLng} latitude={driverLat}>
            <MarkerContent>
              <div className="flex h-9 w-9 items-center justify-center rounded-full border-2 border-amber-400 bg-amber-500 shadow-xl ring-4 ring-amber-300/40">
                <Car className="h-5 w-5 text-black" />
              </div>
            </MarkerContent>
            <MarkerLabel position="top">
              <span className="rounded bg-amber-500 px-2 py-0.5 text-[10px] font-black text-black shadow">
                Captain Live
              </span>
            </MarkerLabel>
            <MarkerPopup>
              <div className="p-2 text-xs">
                <p className="font-bold text-amber-600">Driver Arriving</p>
                <p className="text-gray-600">Live GPS tracking active</p>
              </div>
            </MarkerPopup>
          </MapMarker>
        )}

        {/* Nearby Fleet Vehicles */}
        {activeVehicles.map((v) => (
          <MapMarker key={v.id} longitude={v.lng} latitude={v.lat}>
            <MarkerContent>
              <div
                className="flex h-7 w-7 items-center justify-center rounded-full border border-gray-300 bg-white shadow-md transition-transform hover:scale-110 dark:bg-gray-800"
                style={{ transform: `rotate(${v.rotation ?? 0}deg)` }}
              >
                {v.type === 'bike' || v.type === 'scooty' ? (
                  <Bike className="h-4 w-4 text-emerald-600" />
                ) : (
                  <Car className="h-4 w-4 text-amber-500" />
                )}
              </div>
            </MarkerContent>
            <MarkerPopup>
              <div className="p-1.5 text-[11px]">
                <p className="font-semibold capitalize text-gray-800 dark:text-gray-200">
                  Nearby {v.type}
                </p>
                <p className="text-gray-500">Available for instant ride</p>
              </div>
            </MarkerPopup>
          </MapMarker>
        ))}
      </Map>

      {/* 2. Floating Live ETA & Highway Distance Badge */}
      {etaText && distanceText && (
        <div className="absolute top-3 left-3 z-30 pointer-events-auto flex items-center gap-2 rounded-full border border-gray-200/60 bg-white/95 px-3.5 py-1.5 text-xs font-semibold text-gray-900 shadow-md backdrop-blur dark:border-gray-700/60 dark:bg-gray-900/95 dark:text-gray-100">
          <div className="flex h-5 w-5 items-center justify-center rounded-full bg-emerald-500 text-white shadow-xs">
            <Sparkles className="h-3 w-3" />
          </div>
          <span className="font-bold text-emerald-600 dark:text-emerald-400">{etaText}</span>
          <span className="text-gray-400">•</span>
          <span>{distanceText} (OSRM Highway)</span>
        </div>
      )}

      {/* 3. Floating Quick Control Pill (Zoom In, Zoom Out, 3D Tilt, Locate Me) */}
      <div className="absolute top-3 right-3 z-30 pointer-events-auto flex items-center gap-1.5 rounded-xl border border-gray-200/80 bg-white/95 p-1 shadow-lg backdrop-blur dark:border-gray-700 dark:bg-gray-900/95">
        {/* Style Selector */}
        <select
          value={styleKey}
          onChange={(e) => setStyleKey(e.target.value as StyleKey)}
          className="rounded-lg border border-gray-200 bg-transparent px-2 py-1 text-xs font-medium text-gray-700 focus:outline-none dark:border-gray-700 dark:text-gray-200"
          title="Change Map Basemap Style"
        >
          <option value="bright">OpenFreeMap Bright</option>
          <option value="liberty3d">OpenFreeMap 3D</option>
          <option value="carto">Carto Clean</option>
        </select>

        {/* 3D Pitch Toggle */}
        <button
          type="button"
          onClick={toggle3D}
          className={`flex h-7 px-2 items-center justify-center rounded-lg text-xs font-bold transition-colors ${
            is3D
              ? 'bg-amber-500 text-black shadow-xs'
              : 'text-gray-700 hover:bg-gray-100 dark:text-gray-200 dark:hover:bg-gray-800'
          }`}
          title="Toggle 3D View Angle"
        >
          3D
        </button>

        <div className="h-4 w-px bg-gray-200 dark:bg-gray-700" />

        {/* Zoom In Button */}
        <button
          type="button"
          onClick={handleZoomIn}
          aria-label="Zoom in"
          className="flex h-7 w-7 items-center justify-center rounded-lg text-gray-700 hover:bg-gray-100 active:scale-95 dark:text-gray-200 dark:hover:bg-gray-800"
          title="Zoom In"
        >
          <Plus className="h-4 w-4" />
        </button>

        {/* Zoom Out Button */}
        <button
          type="button"
          onClick={handleZoomOut}
          aria-label="Zoom out"
          className="flex h-7 w-7 items-center justify-center rounded-lg text-gray-700 hover:bg-gray-100 active:scale-95 dark:text-gray-200 dark:hover:bg-gray-800"
          title="Zoom Out"
        >
          <Minus className="h-4 w-4" />
        </button>

        {/* Locate Me Button */}
        <button
          type="button"
          onClick={handleLocateMe}
          aria-label="Locate me"
          className="flex h-7 w-7 items-center justify-center rounded-lg text-gray-700 hover:bg-gray-100 active:scale-95 dark:text-gray-200 dark:hover:bg-gray-800"
          title="Locate Current Position"
        >
          <LocateFixed className="h-4 w-4 text-emerald-600" />
        </button>
      </div>
    </div>
  );
};

export default RideMap;
