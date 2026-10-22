import { Route, Routes } from 'react-router-dom'
import MainLayout from './layouts/MainLayout.jsx'
import Inicio from './pages/Inicio.jsx'
import Lotes from './pages/Lotes.jsx'
import Insumos from './pages/Insumos.jsx'
import Maquinaria from './pages/Maquinaria.jsx'
import ReglasClimaticas from './pages/ReglasClimaticas.jsx'
import Labores from './pages/Labores.jsx'
import Cosechas from './pages/Cosechas.jsx'
import TableroFinanciero from './pages/TableroFinanciero.jsx'
import NoEncontrado from './pages/NoEncontrado.jsx'

function App() {
  return (
    <Routes>
      <Route element={<MainLayout />}>
        <Route index element={<Inicio />} />
        <Route path="lotes" element={<Lotes />} />
        <Route path="insumos" element={<Insumos />} />
        <Route path="maquinaria" element={<Maquinaria />} />
        <Route path="reglas-climaticas" element={<ReglasClimaticas />} />
        <Route path="labores" element={<Labores />} />
        <Route path="cosechas" element={<Cosechas />} />
        <Route path="tablero-financiero" element={<TableroFinanciero />} />
        <Route path="*" element={<NoEncontrado />} />
      </Route>
    </Routes>
  )
}

export default App
