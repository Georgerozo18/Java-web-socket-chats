package Entrega1_Chat_Sockets;

import java.io.*;
import java.net.*;
import java.util.Scanner;

// Cliente del chat - se conecta al servidor, elige un usuario y empieza a chatear
public class ChatClient {

    // IP y puerto del servidor (deben coincidir con ChatServer)
    private static final String IP_SERVIDOR = "127.0.0.1";
    private static final int PUERTO = 59420;

    public static void main(String[] args) {
        Scanner teclado = new Scanner(System.in);

        System.out.println("=== Cliente de Chat ===");
        System.out.println("Conectando a " + IP_SERVIDOR + ":" + PUERTO + "...");

        try (Socket socket = new Socket(IP_SERVIDOR, PUERTO)) {

            System.out.println("Conexion exitosa!\n");

            PrintWriter salida = new PrintWriter(
                new OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true
            );
            BufferedReader entrada = new BufferedReader(
                new InputStreamReader(socket.getInputStream(), "UTF-8")
            );

            // Handshake: intercambio inicial con el servidor (nombre, lista, destinatario)
            // El hilo escuchador NO arranca aqui para evitar que lea mensajes de este flujo
            String lineaServidor;
            while ((lineaServidor = entrada.readLine()) != null) {

                if (lineaServidor.startsWith("--- Chat iniciado")) {
                    System.out.println(lineaServidor);
                    break;
                }

                System.out.println(lineaServidor);

                // Si el servidor espera una respuesta, leerla del teclado y enviarla
                if (lineaServidor.endsWith(":")) {
                    String respuesta = teclado.nextLine();
                    salida.println(respuesta);
                }
            }

            // Una vez iniciado el chat, lanzar el hilo que muestra mensajes entrantes
            EscuchadorServidor escuchador = new EscuchadorServidor(entrada);
            Thread hiloEscucha = new Thread(escuchador);
            hiloEscucha.setDaemon(true);
            hiloEscucha.start();

            // Bucle principal: el usuario escribe mensajes
            System.out.println("(Escribe tus mensajes. 'chao' para salir)\n");

            String mensajeUsuario;
            while (true) {
                mensajeUsuario = teclado.nextLine();
                salida.println(mensajeUsuario);

                if (mensajeUsuario.equalsIgnoreCase("chao")) {
                    System.out.println("Saliste del chat. Hasta luego!");
                    break;
                }
            }

        } catch (ConnectException e) {
            System.err.println("No se pudo conectar. Asegurate de que el servidor este corriendo.");
        } catch (IOException e) {
            System.err.println("Error de conexion: " + e.getMessage());
        } finally {
            teclado.close();
        }
    }
}


// Hilo que escucha mensajes del servidor y los muestra en pantalla
// Corre en paralelo para que el usuario pueda escribir y recibir al mismo tiempo
class EscuchadorServidor implements Runnable {

    private BufferedReader entrada;

    public EscuchadorServidor(BufferedReader entrada) {
        this.entrada = entrada;
    }

    @Override
    public void run() {
        try {
            String mensaje;
            while ((mensaje = entrada.readLine()) != null) {
                System.out.println(mensaje);
            }
        } catch (IOException e) {
            System.out.println("Conexion con el servidor cerrada.");
        }
    }
}
