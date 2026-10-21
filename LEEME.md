# Descarga YT — App creada por Enrique Heredia

Proyecto Android que genera una APK autónoma (no necesita Termux). El motor
(yt-dlp + FFmpeg + Python) va dentro de la propia APK. Los archivos se guardan en
**Descargas/DescargaYT**. También aparece en el menú *Compartir* de YouTube y del navegador.

## Cómo conseguir la APK (gratis, sin instalar nada en el PC)

1. Crea una cuenta en https://github.com y pulsa **New repository**. Ponle un nombre
   (por ejemplo `DescargaYT`), déjalo en **Public** y créalo.
2. En el repositorio nuevo pulsa **uploading an existing file** y arrastra TODO el contenido de
   esta carpeta (incluida la carpeta oculta `.github`). Pulsa **Commit changes**.
   - Si la carpeta `.github` no se subió: **Add file → Create new file**, escribe como nombre
     `.github/workflows/build.yml`, pega el contenido de ese archivo y confirma.
3. Abre la pestaña **Actions**. Verás "Construir APK" ejecutándose (tarda 8–15 minutos la primera vez).
4. Cuando salga el check verde, entra en esa ejecución y, abajo en **Artifacts**,
   descarga **DescargaYT-apk** (un .zip con `DescargaYT.apk` dentro).
5. Pasa la APK al móvil e instálala (Android pedirá permitir "instalar apps de orígenes desconocidos").

### Para tener un enlace de descarga que puedas compartir
En el repositorio: **Releases → Create a new release**, escribe la etiqueta `v1.0`
y publícala. El workflow se ejecuta y adjunta `DescargaYT.apk` a esa release;
ese enlace sirve para compartirla.

## Si el workflow falla (X roja)
Abre la ejecución, entra en el paso en rojo y copia las últimas líneas del error.
- Fallo en "Compilar QuickJS": no es grave, la APK se construye igualmente.
- Fallo en "Compilar APK": suele ser un detalle de versiones; mándame ese error y se corrige.

## Notas
- Requiere Android 10 o superior (arm64, que son casi todos los móviles actuales).
- Deja la app abierta mientras descarga; si Android la cierra en segundo plano, la descarga se corta.
- La app se actualiza el motor (yt-dlp) sola, como mucho cada 12 horas, si hay internet.
- La librería `youtubedl-android` es GPL-3.0. Si repartes la APK, mantén el código del
  repositorio público (así cumples la licencia).
