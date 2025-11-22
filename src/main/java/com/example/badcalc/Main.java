
package com.example.badcalc;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Scanner;
import java.util.concurrent.CopyOnWriteArrayList;


public class Main {

    // Directorio seguro para datos de la aplicación
    private static final Path DATA_DIR;
    private static final Path HISTORY_FILE;
    
    // Límites de seguridad para inputs
    private static final int MAX_NUMERIC_LENGTH = 50;
    
    static {
        try {
            // Inicializar directorio de datos en ubicación segura
            DATA_DIR = Paths.get(System.getProperty("user.home"), ".badcalc");
            HISTORY_FILE = DATA_DIR.resolve("history.txt");
            Files.createDirectories(DATA_DIR);
        } catch (IOException e) {
            throw new ExceptionInInitializerError("No se pudo crear directorio de datos: " + e.getMessage());
        }
    }

    // Historial de operaciones - Lista thread-safe
    private static final CopyOnWriteArrayList<String> history = new CopyOnWriteArrayList<>();

    /**
     * Parsea un string a double con validaciones de seguridad
     * @throws IllegalArgumentException si el input es inválido
     */
    public static double parse(String s) {
        if (s == null || s.trim().isEmpty()) {
            throw new IllegalArgumentException("Input numérico no puede ser nulo o vacío");
        }
        
        s = s.replace(',', '.').trim();
        
        // Prevención de DoS: validar longitud
        if (s.length() > MAX_NUMERIC_LENGTH) {
            throw new IllegalArgumentException("Input numérico demasiado largo (máx " + MAX_NUMERIC_LENGTH + " caracteres)");
        }
        
        try {
            double result = Double.parseDouble(s);
            
            // Validar rangos seguros
            if (Double.isInfinite(result)) {
                throw new IllegalArgumentException("Valor numérico fuera de rango: infinito");
            }
            
            return result;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Input no es un número válido: '" + s + "'", e);
        }
    }

    public static double badSqrt(double v) {
        double g = v;
        int k = 0;
        while (Math.abs(g * g - v) > 0.0001 && k < 100000) {
            g = (g + v / g) / 2.0;
            k++;
        }
        return g;
    }

    public static double compute(String a, String b, String op) {
        double valueA = parse(a);
        double valueB = parse(b);
        try {
            if ("+".equals(op)) return valueA + valueB;
            if ("-".equals(op)) return valueA - valueB;
            if ("*".equals(op)) return valueA * valueB;
            if ("/".equals(op)) {
                if (valueB == 0) {
                    return Double.NaN; // Retorna NaN (Not a Number) para división por cero
                }
                return valueA / valueB;
            }
            if ("^".equals(op)) {
                double z = 1;
                int i = (int) valueB;
                while (i > 0) { z *= valueA; i--; }
                return z;
            }
            if ("%".equals(op)) {
                if (valueB == 0) {
                    return Double.NaN; // Retorna NaN para módulo por cero
                }
                return valueA % valueB;
            }
        } catch (ArithmeticException e) {
            // Error en operación aritmética (ej: overflow, underflow)
            return Double.NaN;
        }
        
        return 0;
    }


    /**
     * Muestra el menú principal de opciones
     */
    @SuppressWarnings("squid:S106") // System.out es la UI de esta aplicación CLI
    private static void displayMenu() {
        System.out.println("CALCULADORA");
        System.out.println("1:+ 2:- 3:* 4:/ 5:^ 6:% 7:hist 0:exit");
        System.out.print("opt: ");
    }

    /**
     * Lee una línea de input de manera segura con validaciones
     */
    private static String readSafeLine(Scanner sc, int maxLength) {
        if (!sc.hasNextLine()) {
            return "";
        }
        String line = sc.nextLine();
        
        // Validar longitud máxima
        if (line.length() > maxLength) {
            throw new IllegalArgumentException("Input excede longitud máxima de " + maxLength + " caracteres");
        }
        
        // Remover caracteres de control peligrosos
        return line.replaceAll("[\\p{Cntrl}&&[^\\r\\n\\t]]", "");
    }

    /**
     * Procesa la opción de historial (opción 8)
     */
    @SuppressWarnings("squid:S106") // System.out para mostrar datos al usuario en CLI
    private static void processHistoryOption() {
        if (history.isEmpty()) {
            System.out.println("[Historial vacío]");
            return;
        }
        for (String h : history) {
            System.out.println(h);
        }
    }

    /**
     * Convierte el código de operación a símbolo
     */
    private static String getOperatorSymbol(String opt) {
        return switch (opt) {
            case "1" -> "+";
            case "2" -> "-";
            case "3" -> "*";
            case "4" -> "/";
            case "5" -> "^";
            case "6" -> "%";
            default -> "";
        };
    }

    /**
     * Guarda la operación en el historial (memoria y archivo) usando path seguro
     */
    @SuppressWarnings("squid:S106") // System.err apropiado para errores en CLI
    private static void saveOperation(String a, String b, String op, double res) {
        String line = a + "|" + b + "|" + op + "|" + res;
        history.add(line);

        // Usar path validado y seguro
        try (FileWriter fw = new FileWriter(HISTORY_FILE.toFile(), true)) {
            fw.write(line + System.lineSeparator());
        } catch (IOException ioe) {
            System.err.println("[ERROR] No se pudo escribir en historial: " + ioe.getMessage());
        }
    }

    /**
     * Procesa operaciones matemáticas estándar con validación de inputs
     */
    @SuppressWarnings("squid:S106") // System.out para I/O de CLI
    private static void processMathOperation(Scanner sc, String opt) {
        try {
            System.out.print("a: ");
            String a = readSafeLine(sc, MAX_NUMERIC_LENGTH);
            System.out.print("b: ");
            String b = readSafeLine(sc, MAX_NUMERIC_LENGTH);

            String op = getOperatorSymbol(opt);
            double res = compute(a, b, op);
            
            if (Double.isNaN(res)) {
                System.out.println("[ERROR] Operación inválida (división por cero o resultado indefinido)");
                return;
            }

            saveOperation(a, b, op, res);
            System.out.println("= " + res);
        } catch (IllegalArgumentException e) {
            System.out.println("[ERROR] Input inválido: " + e.getMessage());
        } catch (Exception e) {
            System.out.println("[ERROR] Error inesperado: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        try (Scanner sc = new Scanner(System.in)) {
            boolean exit = false;
            while (!exit) {
                displayMenu();
                String opt = sc.nextLine();
                
                switch (opt) {
                    case "0" -> exit = true;
                    case "7" -> processHistoryOption();
                    default -> processMathOperation(sc, opt);
                }
            }
        }
    }
}
