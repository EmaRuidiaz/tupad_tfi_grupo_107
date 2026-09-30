-- =============================================================================
-- Sistema AgTech de Gestión y Optimización de Lotes de Cultivo
-- Trabajo Final Integrador (TFI) - Grupo 107
-- Datos Semilla (Seed / Fixtures para desarrollo y demo)
-- =============================================================================

-- 0. Usuarios y Proveedores
INSERT INTO usuario (username, password_hash, rol, email) VALUES
('admin', 'hash_admin', 'ADMIN', 'admin@agtech.com'),
('agronomo1', 'hash_agro', 'AGRONOMO', 'agronomo@agtech.com'),
('operario1', 'hash_oper', 'OPERARIO', 'operador@agtech.com');

INSERT INTO proveedor (nombre, cuit, telefono, email) VALUES
('Agroinsumos del Centro', '30-12345678-9', '02477-123456', 'ventas@agrocentro.com'),
('Semilleria Don Mario', '30-98765432-1', '02477-654321', 'info@donmario.com');

INSERT INTO cultivo (nombre, especie) VALUES
('Soja 1°', 'Soja'),
('Soja 2°', 'Soja'),
('Maíz Tardío', 'Maíz'),
('Trigo', 'Trigo');

-- 1. Lotes de prueba
INSERT INTO lote (nombre, superficie_ha, latitud, longitud, poligono_wkt, tipo_suelo, estado, observaciones, usuario_id) VALUES
('Lote Norte - La Escondida', 120.50, -33.892000, -60.573000, 'POLYGON((-60.573 -33.892, -60.572 -33.892, -60.572 -33.891, -60.573 -33.891, -60.573 -33.892))', 'Franco Limoso', 'SEMBRADO', 'Sembrado con Soja 1.ª. Buen drenaje.', 2),
('Lote Sur - El Bajío', 85.00, -33.915000, -60.591000, 'POLYGON((-60.591 -33.915, -60.590 -33.915, -60.590 -33.914, -60.591 -33.914, -60.591 -33.915))', 'Franco Arcilloso', 'EN_PREPARACION', 'Pendiente de barbecho químico.', 2),
('Lote Este - Las Acacias', 150.00, -33.878000, -60.542000, 'POLYGON((-60.542 -33.878, -60.541 -33.878, -60.541 -33.877, -60.542 -33.877, -60.542 -33.878))', 'Franco Arenoso', 'LISTO_COSECHA', 'Maíz tardío próximo a recolección.', 2);

-- 2. Insumos
INSERT INTO insumo (nombre, categoria, unidad_medida, stock_actual, stock_minimo_alerta, precio_unitario) VALUES
('Semilla Soja Don Mario 46i20', 'SEMILLA', 'BOLSAS', 450.00, 100.00, 55.00),
('Glifosato Roundup Control 48%', 'FITOSANITARIO', 'LITROS', 1200.00, 300.00, 8.50),
('Fertilizante Líquido SolMix', 'FERTILIZANTE', 'LITROS', 3500.00, 1000.00, 1.20),
('Gasoil Grado 2 Agro', 'COMBUSTIBLE', 'LITROS', 5000.00, 1200.00, 1.10),
('Fungicida Curalotodo Pro', 'FITOSANITARIO', 'LITROS', 45.00, 80.00, 32.00); 

-- 3. Maquinaria
INSERT INTO maquinaria (nombre, tipo, matricula_o_serie, horas_uso_actuales, horas_para_proximo_service, consumo_litros_hora, costo_operativo_hora, estado) VALUES
('Tractor John Deere 6110M', 'TRACTOR', 'JD-6110M-2022-01', 985.00, 1000.00, 16.50, 45.00, 'OPERATIVO'),
('Pulverizadora Autopropulsada Caimán SPH', 'PULVERIZADORA', 'CAIMAN-SPH-2021', 1450.00, 1500.00, 22.00, 60.00, 'OPERATIVO'),
('Cosechadora New Holland CR 7.90', 'COSECHADORA', 'NH-CR790-2023', 620.00, 800.00, 38.00, 110.00, 'OPERATIVO');

-- 4. Historial de Mantenimientos
INSERT INTO registro_mantenimiento (maquinaria_id, usuario_id, fecha_service, horas_maquina_en_service, tipo_mantenimiento, costo, proximo_service_horas, descripcion) VALUES
(1, 3, '2026-03-10', 750.00, 'PREVENTIVO_PROGRAMADO', 450.00, 1000.00, 'Cambio de aceite de motor 15W40, filtros de aire y combustible.'),
(2, 3, '2026-04-15', 1200.00, 'CAMBIO_ACEITE_FILTROS', 620.00, 1500.00, 'Calibración de picos de botalón y cambio de filtros hidráulicos.');

-- 5. Reglas Climáticas Agronómicas
INSERT INTO regla_climatica (tipo_labor, viento_max_kmh, viento_min_kmh, temp_min_c, temp_max_c, prob_lluvia_max_pct, humedad_min_pct, mensaje_alerta) VALUES
('PULVERIZACION', 15.00, 2.00, 10.00, 32.00, 20.00, 40.00, 'Viento superior a 15 km/h genera deriva peligrosa de fitosanitarios o lluvia inminente.'),
('SIEMBRA', 28.00, 0.00, 8.00, 35.00, 35.00, 30.00, 'Suelo saturado o precipitaciones excesivas impiden el tránsito de la sembradora.'),
('COSECHA', 35.00, 0.00, 12.00, 40.00, 10.00, 25.00, 'Humedad relativa alta o lluvia impiden la trilla del grano seco.'),
('FERTILIZACION', 22.00, 0.00, 5.00, 35.00, 40.00, 35.00, 'Viento moderado a fuerte provoca dispersión despareja de fertilizante.');

-- 6. Labores Agrícolas
INSERT INTO labor_agricola (lote_id, maquinaria_id, usuario_id, tipo_labor, fecha_planificada, fecha_ejecucion, estado, horas_maquina_consumidas, costo_total_calculado, temperatura_registrada, viento_velocidad_kmh, probabilidad_lluvia_pct, humedad_registrada_pct, observaciones_climaticas) VALUES
(1, 1, 2, 'SIEMBRA', '2026-05-10 08:00:00', '2026-05-10 18:00:00', 'COMPLETADA', 10.00, 12500.00, 18.50, 9.20, 5.00, 45.0, 'Condiciones óptimas. Autorizada por motor de reglas.'),
(1, 2, 2, 'PULVERIZACION', '2026-06-20 09:00:00', '2026-06-20 15:30:00', 'COMPLETADA', 6.50, 4320.00, 21.00, 7.80, 0.00, 50.0, 'Aplicación de herbicida post-emergente.'),
(2, 2, 2, 'PULVERIZACION', '2026-09-18 10:00:00', NULL, 'PLANIFICADA', 0.00, 0.00, NULL, NULL, NULL, NULL, 'Programada para validación con pronóstico del día.');

-- 7. Consumo de Insumos por Labor
INSERT INTO labor_insumo (labor_id, insumo_id, cantidad_utilizada, costo_subtotal) VALUES
(1, 1, 120.00, 6600.00),
(1, 4, 165.00, 181.50),
(2, 2, 240.00, 2040.00),
(2, 4, 143.00, 157.30);

-- 8. Compras y Movimientos de Stock
INSERT INTO compra_insumo (proveedor_id, usuario_id, fecha_compra, total_compra) VALUES
(1, 1, '2026-05-01 10:00:00', 50000.00);

INSERT INTO detalle_compra (compra_id, insumo_id, cantidad, precio_unitario, subtotal) VALUES
(1, 1, 570.00, 55.00, 31350.00);

INSERT INTO movimiento_stock (insumo_id, fecha, tipo, cantidad, usuario_id, labor_id, compra_id, observacion) VALUES
(1, '2026-05-01 10:00:00', 'INGRESO_COMPRA', 570.00, 1, NULL, 1, 'Compra inicial de temporada'),
(1, '2026-05-10 18:00:00', 'EGRESO_LABOR', -120.00, 2, 1, NULL, 'Consumo en labor de siembra Lote Norte');
