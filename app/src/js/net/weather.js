/* ═══════════════════════════════════════════════════════════
   Погода через Open‑Meteo (без ключей, CORS открыт).
   Геокодинг: пробуем формы города (именительный из предложного).
   ═══════════════════════════════════════════════════════════ */
import { fetchJSON } from '../netutil.js';

export const WEATHER_CODES = {
  0: 'ясно', 1: 'преимущественно ясно', 2: 'переменная облачность', 3: 'пасмурно',
  45: 'туман', 48: 'туман с изморозью',
  51: 'морось слабая', 53: 'морось', 55: 'морось сильная',
  56: 'ледяная морось слабая', 57: 'ледяная морось',
  61: 'дождь слабый', 63: 'дождь', 65: 'дождь сильный',
  66: 'ледяной дождь слабый', 67: 'ледяной дождь',
  71: 'снегопад слабый', 73: 'снегопад', 75: 'снегопад сильный', 77: 'снежные зёрна',
  80: 'ливень слабый', 81: 'ливень', 82: 'ливень сильный',
  85: 'снегопад слабый', 86: 'снегопад сильный',
  95: 'гроза', 96: 'гроза с градом', 99: 'сильная гроза с градом',
};

/** «Самаре» → [«Самаре», «Самара», «Самар»], «Казани» → [«Казани», «Казань», «Казан»]… */
export function cityCandidates(raw) {
  const name = String(raw || '').trim();
  if (!name) return [];
  const out = [name];
  const lower = name.toLowerCase();
  if (lower.endsWith('е')) { out.push(name.slice(0, -1) + 'а', name.slice(0, -1)); }        // Самаре→Самара, Новосибирске→Новосибирск
  if (lower.endsWith('и')) { out.push(name.slice(0, -1) + 'ь', name.slice(0, -1) + 'й', name.slice(0, -1)); } // Казани→Казань
  if (lower.endsWith('ю')) { out.push(name.slice(0, -1) + 'я', name.slice(0, -1)); }        // в Уфу/в Москву
  if (lower.endsWith('я')) { out.push(name.slice(0, -1) + 'ь'); }                           // Астане… мало ли
  if (lower.endsWith('а')) { out.push(name.slice(0, -1) + 'е'); }                           // уже в предложном
  if (lower.endsWith('ь')) { out.push(name.slice(0, -1) + 'и'); }
  return [...new Set(out)];
}

/** Пытается найти город. Возвращает {ok, place, country, latitude, longitude, candidates} или {ok:false, error}. */
export async function geocodeCity(raw) {
  const candidates = cityCandidates(raw);
  let lastError = null;
  for (const candidate of candidates) {
    try {
      const data = await fetchJSON(
        `https://geocoding-api.open-meteo.com/v1/search?name=${encodeURIComponent(candidate)}&count=1&language=ru`,
        { timeout: 7000 },
      );
      if (data?.results?.length) {
        const r = data.results[0];
        return {
          ok: true,
          place: r.name,
          country: r.country || '',
          admin: r.admin1 || '',
          latitude: r.latitude,
          longitude: r.longitude,
        };
      }
    } catch (e) {
      lastError = e;
    }
  }
  return { ok: false, error: lastError ? String(lastError.message || lastError) : 'город не найден' };
}

/** Текущая погода по координатам. */
export async function getCurrentWeather(latitude, longitude) {
  try {
    const data = await fetchJSON(
      `https://api.open-meteo.com/v1/forecast?latitude=${latitude}&longitude=${longitude}` +
      `&current=temperature_2m,relative_humidity_2m,apparent_temperature,weather_code,wind_speed_10m` +
      `&wind_speed_unit=ms&timezone=auto`,
      { timeout: 7000 },
    );
    const c = data?.current;
    if (!c) return { ok: false, error: 'пустой ответ сервиса погоды' };
    return {
      ok: true,
      temperature: c.temperature_2m,
      apparent: c.apparent_temperature,
      humidity: c.relative_humidity_2m,
      windSpeed: c.wind_speed_10m,
      code: c.weather_code,
      codeText: WEATHER_CODES[c.weather_code] || `код ${c.weather_code}`,
      time: c.time,
    };
  } catch (e) {
    return { ok: false, error: String(e?.message || e) };
  }
}

/** Полный цикл: «Самаре» → данные. */
export async function getWeatherForCity(rawCity) {
  const geo = await geocodeCity(rawCity);
  if (!geo.ok) return { ok: false, cityInput: rawCity, error: geo.error };
  const weather = await getCurrentWeather(geo.latitude, geo.longitude);
  if (!weather.ok) return { ...weather, place: geo.place, country: geo.country, cityInput: rawCity };
  return { ...weather, place: geo.place, country: geo.country, admin: geo.admin, cityInput: rawCity };
}

/** Координаты устройства (если пользователь дал разрешение). */
export function getDevicePosition() {
  return new Promise((resolve) => {
    if (typeof navigator === 'undefined' || !navigator.geolocation) return resolve(null);
    navigator.geolocation.getCurrentPosition(
      (pos) => resolve({ latitude: pos.coords.latitude, longitude: pos.coords.longitude }),
      () => resolve(null),
      { timeout: 6000, maximumAge: 30 * 60 * 1000 },
    );
  });
}
