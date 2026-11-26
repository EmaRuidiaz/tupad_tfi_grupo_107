import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { getSession, tieneAlgunRol } from '../services/api.js'
import { listarInsumos } from '../services/insumosService.js'
import { listarLabores } from '../services/laboresService.js'
import { listarLotes } from '../services/lotesService.js'
import { listarMaquinarias } from '../services/maquinariaService.js'
import { formatearNumero } from '../utils/formatters.js'

const ESTADOS_LOTE_ACTIVOS = new Set([
  'SEMBRADO',
  'EN_CRECIMIENTO',
  'LISTO_COSECHA',
])

const MODULOS = [
  {
    path: '/lotes',
    titulo: 'Lotes',
    categoria: 'Campo',
    descripcion:
      'Administración de parcelas, superficie productiva, estado agronómico y condiciones climáticas actuales.',
  },
  {
    path: '/labores',
    titulo: 'Labores',
    categoria: 'Operaciones',
    descripcion:
      'Programación de órdenes de trabajo, validación meteorológica, reserva de insumos y registro de cierre.',
  },
  {
    path: '/insumos',
    titulo: 'Insumos',
    categoria: 'Inventario',
    descripcion:
      'Control de stock físico, disponible y reservado, ajustes manuales e historial de movimientos.',
  },
  {
    path: '/compras',
    titulo: 'Compras',
    categoria: 'Abastecimiento',
    descripcion:
      'Carga de facturas de proveedores con ingreso automático a depósito y actualización de precios.',
  },
  {
    path: '/maquinaria',
    titulo: 'Maquinaria',
    categoria: 'Flota',
    descripcion:
      'Seguimiento de tractores y equipos, horas de uso, costo operativo por hora y alertas de service.',
  },
  {
    path: '/reglas-climaticas',
    titulo: 'Reglas climáticas',
    categoria: 'Clima',
    descripcion:
      'Definición de umbrales de viento, temperatura, lluvia y humedad para habilitar tareas en campo.',
  },
  {
    path: '/cosechas',
    titulo: 'Cosechas',
    categoria: 'Producción',
    descripcion:
      'Registro de rendimientos obtenidos por lote, cierre de ciclo productivo e ingreso bruto.',
  },
  {
    path: '/tablero-financiero',
    titulo: 'Tablero financiero',
    categoria: 'Análisis',
    soloGestion: true,
    descripcion:
      'Comparativa de ingresos vs. costos acumulados, margen neto, costo por hectárea y retorno de inversión.',
  },
]

const PASOS_FLUJO = [
  {
    numero: '01',
    titulo: 'Lotes e insumos',
    detalle: 'Definí parcelas activas y verificá stock antes de operar.',
  },
  {
    numero: '02',
    titulo: 'Planificación y clima',
    detalle: 'Programá órdenes y validá condiciones antes de salir a campo.',
  },
  {
    numero: '03',
    titulo: 'Ejecución y cierre',
    detalle: 'Registrá consumo real de insumos y horas de maquinaria.',
  },
  {
    numero: '04',
    titulo: 'Cosecha y rentabilidad',
    detalle: 'Cerrá el ciclo y evaluá margen neto y ROI por lote.',
  },
]

function calcularResumen({ lotes, insumos, maquinarias, labores }) {
  const hectareasTotales = lotes.reduce(
    (acc, lote) => acc + Number(lote.superficieHa || 0),
    0,
  )
  const lotesActivos = lotes.filter((l) =>
    ESTADOS_LOTE_ACTIVOS.has(l.estado),
  ).length

  const insumosCriticos = insumos.filter((i) => i.stockCritico).length

  const maquinasOperativas = maquinarias.filter(
    (m) => m.estado === 'OPERATIVO',
  ).length
  const maquinasServiceVencido = maquinarias.filter(
    (m) => m.mantenimientoVencido,
  ).length

  const laboresEnCurso = labores.filter(
    (l) => l.estado === 'EN_EJECUCION',
  ).length
  const laboresPendientes = labores.filter(
    (l) => l.estado === 'PLANIFICADA' || l.estado === 'APROBADA_CLIMA',
  ).length
  const laboresBloqueadas = labores.filter(
    (l) => l.estado === 'BLOQUEADA_CLIMA',
  ).length

  return {
    totalLotes: lotes.length,
    hectareasTotales,
    lotesActivos,
    totalInsumos: insumos.length,
    insumosCriticos,
    totalMaquinarias: maquinarias.length,
    maquinasOperativas,
    maquinasServiceVencido,
    laboresEnCurso,
    laboresPendientes,
    laboresBloqueadas,
  }
}

function Inicio() {
  const sesion = getSession()
  const puedeVerFinanzas = tieneAlgunRol('ADMIN', 'AGRONOMO')
  const [cargando, setCargando] = useState(true)
  const [resumen, setResumen] = useState(null)
  const [avisoCarga, setAvisoCarga] = useState(false)

  useEffect(() => {
    let cancelado = false

    async function cargarResumen() {
      const [resLotes, resInsumos, resMaq, resLabores] =
        await Promise.allSettled([
          listarLotes(),
          listarInsumos(),
          listarMaquinarias(),
          listarLabores(),
        ])

      if (cancelado) return

      const todasFallaron = [resLotes, resInsumos, resMaq, resLabores].every(
        (r) => r.status === 'rejected',
      )

      if (todasFallaron) {
        setAvisoCarga(true)
        setCargando(false)
        return
      }

      setResumen(
        calcularResumen({
          lotes: resLotes.status === 'fulfilled' ? resLotes.value || [] : [],
          insumos:
            resInsumos.status === 'fulfilled' ? resInsumos.value || [] : [],
          maquinarias:
            resMaq.status === 'fulfilled' ? resMaq.value || [] : [],
          labores:
            resLabores.status === 'fulfilled' ? resLabores.value || [] : [],
        }),
      )
      setCargando(false)
    }

    cargarResumen()
    return () => {
      cancelado = true
    }
  }, [])

  const modulosVisibles = MODULOS.filter(
    (m) => !m.soloGestion || puedeVerFinanzas,
  )

  return (
    <section className="pagina inicio">
      <header className="inicio-cabecera">
        <div className="inicio-cabecera__texto">
          <div className="inicio-cabecera__meta">
            <span className="etiqueta etiqueta--ok">Sistema operativo</span>
            {sesion?.username && (
              <span className="texto-suave texto-chico">
                Sesión: <strong>{sesion.username}</strong> ({sesion.rol})
              </span>
            )}
          </div>
          <h1>Sistema AgTech</h1>
          <p className="pagina__subtitulo">
            Plataforma integral para la gestión agronómica, control de insumos y
            maquinaria, validación climática y análisis económico por lote.
          </p>
        </div>

        <div className="inicio-cabecera__acciones">
          <Link to="/labores" className="btn btn--primario">
            Ir a Labores
          </Link>
          <Link
            to={puedeVerFinanzas ? '/tablero-financiero' : '/lotes'}
            className="btn btn--secundario"
          >
            {puedeVerFinanzas ? 'Tablero financiero' : 'Ver Lotes'}
          </Link>
        </div>
      </header>

      {avisoCarga && (
        <div className="aviso">
          No se pudieron actualizar los indicadores en este momento. Podés
          navegar directamente a cualquier módulo desde abajo.
        </div>
      )}

      <div className="tarjetas-resumen" aria-label="Indicadores generales">
        <div className="tarjeta-kpi">
          <span className="tarjeta-kpi__titulo">Lotes registrados</span>
          <strong className="tarjeta-kpi__valor">
            {cargando || !resumen ? '—' : resumen.totalLotes}
          </strong>
          <span className="tarjeta-kpi__detalle">
            {cargando || !resumen
              ? 'Cargando superficie...'
              : `${formatearNumero(resumen.hectareasTotales)} ha totales · ${resumen.lotesActivos} en cultivo`}
          </span>
        </div>

        <div className="tarjeta-kpi">
          <span className="tarjeta-kpi__titulo">Labores activas</span>
          <strong className="tarjeta-kpi__valor">
            {cargando || !resumen
              ? '—'
              : resumen.laboresEnCurso + resumen.laboresPendientes}
          </strong>
          <span className="tarjeta-kpi__detalle">
            {cargando || !resumen
              ? 'Cargando órdenes...'
              : `${resumen.laboresEnCurso} en ejecución · ${resumen.laboresPendientes} pendientes${
                  resumen.laboresBloqueadas > 0
                    ? ` · ${resumen.laboresBloqueadas} bloq. clima`
                    : ''
                }`}
          </span>
        </div>

        <div
          className={`tarjeta-kpi ${
            resumen?.insumosCriticos > 0 ? 'tarjeta-kpi--alerta' : ''
          }`}
        >
          <span className="tarjeta-kpi__titulo">Inventario de insumos</span>
          <strong className="tarjeta-kpi__valor">
            {cargando || !resumen ? '—' : resumen.totalInsumos}
          </strong>
          <span className="tarjeta-kpi__detalle">
            {cargando || !resumen
              ? 'Cargando stock...'
              : resumen.insumosCriticos > 0
                ? `${resumen.insumosCriticos} con stock crítico`
                : 'Sin alertas de stock'}
          </span>
        </div>

        <div
          className={`tarjeta-kpi ${
            resumen?.maquinasServiceVencido > 0 ? 'tarjeta-kpi--alerta' : ''
          }`}
        >
          <span className="tarjeta-kpi__titulo">Flota de maquinaria</span>
          <strong className="tarjeta-kpi__valor">
            {cargando || !resumen
              ? '—'
              : `${resumen.maquinasOperativas}/${resumen.totalMaquinarias}`}
          </strong>
          <span className="tarjeta-kpi__detalle">
            {cargando || !resumen
              ? 'Cargando equipos...'
              : resumen.maquinasServiceVencido > 0
                ? `${resumen.maquinasServiceVencido} con service pendiente`
                : 'Equipos operativos al día'}
          </span>
        </div>
      </div>

      <section className="inicio-seccion">
        <div className="inicio-seccion__encabezado">
          <h2>Módulos de gestión</h2>
          <p className="texto-suave texto-chico">
            Acceso directo a las áreas operativas del sistema
          </p>
        </div>

        <div className="inicio-modulos">
          {modulosVisibles.map((modulo) => (
            <Link
              key={modulo.path}
              to={modulo.path}
              className="inicio-modulo"
            >
              <div className="inicio-modulo__cabecera">
                <h3>{modulo.titulo}</h3>
                <span className="inicio-modulo__categoria">
                  {modulo.categoria}
                </span>
              </div>
              <p className="inicio-modulo__desc">{modulo.descripcion}</p>
              <span className="inicio-modulo__enlace">Ingresar →</span>
            </Link>
          ))}
        </div>
      </section>

      <section className="inicio-flujo" aria-label="Flujo de trabajo sugerido">
        <h2 className="inicio-flujo__titulo">Ciclo operativo de campaña</h2>
        <div className="inicio-flujo__pasos">
          {PASOS_FLUJO.map((paso) => (
            <div key={paso.numero} className="inicio-paso">
              <span className="inicio-paso__num">{paso.numero}</span>
              <div>
                <strong>{paso.titulo}</strong>
                <p>{paso.detalle}</p>
              </div>
            </div>
          ))}
        </div>
      </section>
    </section>
  )
}

export default Inicio
