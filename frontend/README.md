# Frontend - Sistema AgTech (Grupo 107)

SPA en **React** creada con **Vite**, con navegación mediante **React Router**.

## Requisitos

- Node.js 20 o superior (probado con Node 22)
- Backend corriendo en `http://localhost:8080` (ver [`backend/README.md`](../backend/README.md))

## Cómo ejecutarlo

```bash
cd frontend
npm install
npm run dev
```

La aplicación queda disponible en http://localhost:5173 (puerto permitido por la configuración CORS del backend).

## Configuración

| Variable | Default | Uso |
| :--- | :--- | :--- |
| `VITE_API_URL` | `http://localhost:8080/api` | URL base de la API REST. Se define en un archivo `.env.local` (ignorado por git) o como variable de entorno al hacer el build |

## Autenticación (HU-14)

- Todas las pantallas, salvo `/login`, requieren sesión. Sin sesión, `MainLayout` redirige a `/login` y, al ingresar, vuelve a la pantalla pedida.
- El token JWT, el usuario y el rol se guardan en `localStorage` y se descartan al vencer (8 horas).
- `src/services/api.js` expone `apiFetch()`, que agrega `Authorization: Bearer <token>` a cada request. Ante un `401` cierra la sesión y redirige al login.
- En desarrollo (`npm run dev`) el login muestra accesos rápidos con los usuarios semilla (`admin`, `agronomo1`, `operario1`, clave `agtech123`). **No aparecen en el build de producción.**

> Ocultar botones según el rol (`isAdmin()`) es sólo una ayuda visual: los permisos reales los aplica el backend.

## Scripts

| Comando           | Descripción                                  |
| :---------------- | :------------------------------------------- |
| `npm run dev`     | Servidor de desarrollo con recarga en vivo   |
| `npm run build`   | Genera el build de producción en `dist/`     |
| `npm run preview` | Sirve localmente el build generado           |
| `npm run lint`    | Analiza el código con oxlint                 |

## Estructura

```text
frontend/
├── public/              # Archivos estáticos (favicon)
└── src/
    ├── components/      # Componentes reutilizables (Sidebar, etc.)
    ├── layouts/         # Estructura común de las pantallas (menú + contenido) y protección de rutas
    ├── pages/           # Una pantalla por módulo del sistema (+ Login)
    ├── routes/          # Opciones del menú lateral
    ├── services/        # Cliente HTTP (api.js) y un servicio por recurso de la API
    ├── App.jsx          # Definición de rutas
    ├── main.jsx         # Punto de entrada
    └── index.css        # Estilos globales y clases reutilizables (btn, tabla, modal, etc.)
```

## Agregar una pantalla nueva

1. Crear el componente en `src/pages/`.
2. Registrar la ruta en `src/App.jsx` (dentro de `MainLayout` si requiere sesión).
3. Agregar la opción en `src/routes/menuItems.js` para que aparezca en el menú.
4. Si consume la API, crear `src/services/<recurso>Service.js` usando `apiFetch()` (ver `insumosService.js`).
