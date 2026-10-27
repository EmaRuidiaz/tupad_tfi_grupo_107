package com.grupo107.agtech.service;

import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Valida polígonos en formato WKT ({@code POLYGON((lon lat, lon lat, ...))}) sin depender
 * de una librería GIS. Cada anillo debe tener al menos 4 puntos y estar cerrado.
 */
final class PoligonoWktValidator {

    private static final Pattern POLYGON = Pattern.compile("^POLYGON\\s*\\((.*)\\)$",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private PoligonoWktValidator() {
    }

    /** @throws IllegalArgumentException con el detalle del error de formato. */
    static void validar(String wkt) {
        Matcher matcher = POLYGON.matcher(wkt.trim());
        if (!matcher.matches()) {
            throw new IllegalArgumentException("debe tener la forma POLYGON((lon lat, lon lat, ...))");
        }

        String contenido = matcher.group(1).trim();
        if (!contenido.startsWith("(") || !contenido.endsWith(")")) {
            throw new IllegalArgumentException("cada anillo debe ir entre paréntesis");
        }

        String[] anillos = contenido.substring(1, contenido.length() - 1).split("\\)\\s*,\\s*\\(");
        for (String anillo : anillos) {
            validarAnillo(anillo);
        }
    }

    private static void validarAnillo(String anillo) {
        if (anillo.contains("(") || anillo.contains(")")) {
            throw new IllegalArgumentException("paréntesis mal balanceados");
        }

        String[] puntos = anillo.split(",");
        if (puntos.length < 4) {
            throw new IllegalArgumentException("cada anillo necesita al menos 4 puntos");
        }

        double[] primero = parsearPunto(puntos[0]);
        double[] ultimo = primero;
        for (int i = 1; i < puntos.length; i++) {
            ultimo = parsearPunto(puntos[i]);
        }

        if (!Arrays.equals(primero, ultimo)) {
            throw new IllegalArgumentException("el anillo debe estar cerrado (el primer y el último punto deben coincidir)");
        }
    }

    private static double[] parsearPunto(String punto) {
        String texto = punto.trim();
        String[] coordenadas = texto.split("\\s+");
        if (coordenadas.length != 2) {
            throw new IllegalArgumentException("cada punto debe tener longitud y latitud separadas por un espacio: '" + texto + "'");
        }

        double longitud;
        double latitud;
        try {
            longitud = Double.parseDouble(coordenadas[0]);
            latitud = Double.parseDouble(coordenadas[1]);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("coordenada no numérica: '" + texto + "'");
        }

        if (longitud < -180 || longitud > 180 || latitud < -90 || latitud > 90) {
            throw new IllegalArgumentException("coordenada fuera de rango: '" + texto + "'");
        }
        return new double[]{longitud, latitud};
    }
}
