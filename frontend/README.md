# Frontend - Sistema AgTech (Grupo 107)

SPA en **React** creada con **Vite**, con navegación mediante **React Router**.

## Requisitos

- Node.js 20 o superior (probado con Node 22)

## Cómo ejecutarlo

```bash
cd frontend
npm install
npm run dev
```

La aplicación queda disponible en http://localhost:5173 (puerto permitido por la configuración CORS del backend).

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
    ├── layouts/         # Estructura común de las pantallas (menú + contenido)
    ├── pages/           # Una pantalla por módulo del sistema
    ├── routes/          # Opciones del menú lateral
    ├── App.jsx          # Definición de rutas
    ├── main.jsx         # Punto de entrada
    └── index.css        # Estilos globales
```

## Agregar una pantalla nueva

1. Crear el componente en `src/pages/`.
2. Registrar la ruta en `src/App.jsx`.
3. Agregar la opción en `src/routes/menuItems.js` para que aparezca en el menú.
