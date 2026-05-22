package Entrega1_Chat_Sockets;

import java.io.*;
import java.net.*;
import java.util.Scanner;

/**
 * ENTREGA 1 - Cliente de Chat con Sockets
 *
 * Este programa es el que ejecuta cada usuario que quiere conectarse al chat.
 * Se conecta al servidor usando su IP y puerto, envia el nombre de usuario,
 * recibe la lista de conectados, elige con quien chatear y empieza la conversacion.
 *
 * Para salir del chat, el usuario escribe "chao".
 *
 * Como ejecutar (desde la terminal, en la carpeta raiz del proyecto):
 *   javac Entrega1_Chat_Sockets/ChatClient.java
 *   java  Entrega1_Chat_Sockets.ChatClient
 */
public class ChatClient {

    // IP del servidor (127.0.0.1 = localhost = la misma maquina)
    // Cambiar esta IP si el servidor corre en otra maquina de la red
    private static final String IP_SERVIDOR = "127.0.0.1";

    // Puerto del servidor (debe coincidir exactamente con ChatServer.PUERTO)
    private static final int PUERTO = 59420;

    public static void main(String[] args) {
        Scanner teclado = new Scanner(System.in);

        System.out.println("=== Cliente de Chat ===");
        System.out.println("Conectando a " + IP_SERVIDOR + ":" + PUERTO + "...");

        // Socket es el "enchufe" del cliente: se conecta al servidor
        // Si el servidor no esta corriendo, lanza ConnectException
        try (Socket socket = new Socket(IP_SERVIDOR, PUERTO)) {

            System.out.println("Conexion exitosa con el servidor!\n");

            // Canal de SALIDA: para enviar mensajes al servidor
            PrintWriter salida = new PrintWriter(
                new OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true
            );

            // Canal de ENTRADA: para recibir mensajes del servidor
            BufferedReader entrada = new BufferedReader(
                new InputStreamReader(socket.getInputStream(), "UTF-8")
            );

            // --- PASO 1: Handshake inicial (nombre, lista de usuarios, destinatario) ---
            // El hilo escuchador NO arranca todavia porque ambos leeran del mismo canal
            // y se "robarian" mensajes entre si. Primero completamos el handshake de forma
            // secuencial, y solo despues lanzamos el hilo para mensajes del chat en vivo.

            String lineaServidor;
            while ((lineaServidor = entrada.readLine()) != null) {

                // Cuando el servidor confirma que el chat inicio, salimos del handshake
                if (lineaServidor.startsWith("--- Chat iniciado")) {
                    System.out.println(lineaServidor);
                    break;
                }

                System.out.println(lineaServidor);

                // Si el servidor termina la linea con ":", espera una respuesta del usuario
                if (lineaServidor.endsWith(":")) {
                    String respuesta = teclado.nextLine();
                    salida.println(respuesta);
                }
            }

            // --- PASO 2: Ahora si lanzamos el hilo escuchador ---
            // A partir de aqui los mensajes que llegan son del chat en vivo (del otro usuario)
            // El hilo los muestra en pantalla mientras el main espera que el usuario escriba
            EscuchadorServidor escuchador = new EscuchadorServidor(entrada);
            Thread hiloEscucha = new Thread(escuchador);
            hiloEscucha.setDaemon(true); // Hilo daemon: muere automaticamente cuando el main termina
            hiloEscucha.start();

            // --- PASO 3: Bucle principal - el usuario escribe mensajes ---
            System.out.println("(Escribe tus mensajes. 'chao' para salir)\n");

            String mensajeUsuario;
            while (true) {
                // nextLine() espera que el usuario presione Enter
                mensajeUsuario = teclado.nextLine();

                // Enviamos el mensaje al servidor (quien lo reenvia al destinatario)
                salida.println(mensajeUsuario);

                // Si el usuario escribio "chao", terminamos
                if (mensajeUsuario.equalsIgnoreCase("chao")) {
                    System.out.println("Saliste del chat. Hasta luego!");
                    break;
                }
            }

        } catch (ConnectException e) {
            // ConnectException ocurre cuando el servidor no esta corriendo
            System.err.println("No se pudo conectar al servidor. Asegurate de que ChatServer este corriendo.");
        } catch (IOException e) {
            System.err.println("Error de conexion: " + e.getMessage());
        } finally {
            teclado.close();
        }
    }
}


// =============================================================================
// CLASE EscuchadorServidor
// Corre en un hilo separado y se dedica SOLO a recibir mensajes del servidor
// y mostrarlos en pantalla. Esto permite que el usuario pueda escribir y recibir
// mensajes al mismo tiempo (entrada/salida concurrente).
// =============================================================================
class EscuchadorServidor implements Runnable {

    private BufferedReader entrada; // Canal de lectura del socket

    public EscuchadorServidor(BufferedReader entrada) {
        this.entrada = entrada;
    }

    @Override
    public void run() {
        try {
            String mensaje;
            // Mientras haya conexion, leer y mostrar cada mensaje que llega del servidor
            while ((mensaje = entrada.readLine()) != null) {
                System.out.println(mensaje);
            }
        } catch (IOException e) {
            // Si el servidor se cierra, esta excepcion es normal
            System.out.println("Conexion con el servidor cerrada.");
        }
    }
}
