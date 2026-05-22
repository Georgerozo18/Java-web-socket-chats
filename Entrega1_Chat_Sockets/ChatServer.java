package Entrega1_Chat_Sockets;

import java.io.*;
import java.net.*;
import java.util.*;

/**
 * ENTREGA 1 - Servidor de Chat con Sockets
 *
 * Este programa actua como servidor central del chat.
 * Escucha conexiones entrantes de clientes en un puerto especifico,
 * registra cada usuario que se conecta y enruta los mensajes entre ellos.
 *
 * Flujo:
 *   1. El servidor arranca y queda esperando conexiones (puerto 59420)
 *   2. Cada vez que un cliente se conecta, se crea un hilo (ClienteHandler) para atenderlo
 *   3. El cliente envia su nombre de usuario
 *   4. El servidor le devuelve la lista de usuarios conectados
 *   5. El cliente elige con quien chatear
 *   6. Los mensajes se reenvian al destinatario elegido
 *   7. Si el cliente envia "chao", se desconecta y el servidor lo notifica
 */
public class ChatServer {

    // Puerto donde el servidor escucha conexiones (igual al del PDF)
    private static final int PUERTO = 59420;

    // Mapa compartido entre todos los hilos: nombre de usuario -> su hilo manejador
    // ConcurrentHashMap es thread-safe: varios hilos pueden leer/escribir sin conflictos
    private static Map<String, ClienteHandler> usuariosConectados = new HashMap<>();

    public static void main(String[] args) {
        System.out.println("=== Servidor de Chat iniciado ===");
        System.out.println("Escuchando en puerto: " + PUERTO);
        System.out.println("Esperando conexiones de clientes...\n");

        // ServerSocket es el "enchufe" del servidor: espera que alguien se conecte
        try (ServerSocket serverSocket = new ServerSocket(PUERTO)) {

            // Bucle infinito: el servidor nunca para de aceptar nuevos clientes
            while (true) {
                // accept() BLOQUEA la ejecucion hasta que llega un cliente
                // Cuando llega, retorna un Socket con la conexion establecida
                Socket socketCliente = serverSocket.accept();

                System.out.println("Nueva conexion entrante desde: " + socketCliente.getInetAddress());

                // Se crea un hilo dedicado para este cliente y se lanza
                // Asi el servidor puede seguir aceptando mas clientes en paralelo
                ClienteHandler manejador = new ClienteHandler(socketCliente);
                new Thread(manejador).start();
            }

        } catch (IOException e) {
            System.err.println("Error en el servidor: " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Metodos estaticos para gestionar la lista de usuarios conectados
    // Son estaticos para que ClienteHandler (clase interna) los pueda usar
    // -------------------------------------------------------------------------

    /** Registra un nuevo usuario en el mapa de conectados */
    public static synchronized void registrarUsuario(String nombre, ClienteHandler handler) {
        usuariosConectados.put(nombre, handler);
        System.out.println("Usuario \"" + nombre + "\" conectado. Total: " + usuariosConectados.size());
    }

    /** Elimina un usuario del mapa cuando se desconecta */
    public static synchronized void desconectarUsuario(String nombre) {
        usuariosConectados.remove(nombre);
        System.out.println("El usuario \"" + nombre + "\" abandono el chat.");
    }

    /** Devuelve la lista de nombres de usuarios actualmente conectados */
    public static synchronized List<String> getUsuariosConectados() {
        return new ArrayList<>(usuariosConectados.keySet());
    }

    /** Busca el handler de un usuario por nombre (para enviarle un mensaje) */
    public static synchronized ClienteHandler getHandler(String nombre) {
        return usuariosConectados.get(nombre);
    }
}


// =============================================================================
// CLASE ClienteHandler
// Cada instancia de esta clase maneja la comunicacion con UN cliente especifico.
// Corre en su propio hilo para no bloquear al servidor ni a otros clientes.
// =============================================================================
class ClienteHandler implements Runnable {

    private Socket socket;           // Conexion con este cliente
    private PrintWriter salida;      // Para ENVIAR mensajes al cliente
    private BufferedReader entrada;  // Para RECIBIR mensajes del cliente
    private String nombreUsuario;    // Nombre con el que se identifico este cliente

    public ClienteHandler(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        try {
            // Preparamos los canales de lectura y escritura sobre el socket
            // InputStreamReader convierte bytes en caracteres (UTF-8)
            entrada = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
            // PrintWriter con autoFlush=true: envia el mensaje inmediatamente sin necesidad de flush()
            salida = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true);

            // --- PASO 1: El cliente envia su nombre de usuario ---
            salida.println("Bienvenido al Chat del Politecnico. Ingresa tu nombre de usuario:");
            nombreUsuario = entrada.readLine();

            if (nombreUsuario == null || nombreUsuario.trim().isEmpty()) {
                salida.println("Nombre invalido. Desconectando.");
                socket.close();
                return;
            }

            nombreUsuario = nombreUsuario.trim();

            // Registramos este usuario en el mapa global del servidor
            ChatServer.registrarUsuario(nombreUsuario, this);

            // --- PASO 2: Enviar lista de usuarios conectados ---
            enviarListaUsuarios();

            // --- PASO 3: El cliente elige con quien chatear ---
            salida.println("Escribe el nombre del usuario con quien quieres chatear:");
            String destinatario = entrada.readLine();

            if (destinatario == null) {
                desconectar();
                return;
            }

            destinatario = destinatario.trim();
            ClienteHandler handlerDestinatario = ChatServer.getHandler(destinatario);

            if (handlerDestinatario == null) {
                salida.println("El usuario \"" + destinatario + "\" no esta conectado.");
                desconectar();
                return;
            }

            // Notificar a ambos usuarios que el chat comenzo
            salida.println("--- Chat iniciado con " + destinatario + ". Escribe 'chao' para salir. ---");
            handlerDestinatario.enviarMensaje("--- " + nombreUsuario + " quiere chatear contigo. ---");

            // --- PASO 4: Bucle principal de mensajes ---
            String mensaje;
            while ((mensaje = entrada.readLine()) != null) {

                // Si el mensaje es "chao", terminamos la sesion
                if (mensaje.equalsIgnoreCase("chao")) {
                    // Notificar al otro usuario que este se fue
                    handlerDestinatario.enviarMensaje("--- " + nombreUsuario + " abandono el chat (chao). ---");
                    salida.println("--- Has salido del chat. Hasta luego! ---");
                    break;
                }

                // Reenviar el mensaje al destinatario con el formato: "Poli01--> mensaje"
                String mensajeFormateado = nombreUsuario + "--> " + mensaje;
                handlerDestinatario.enviarMensaje(mensajeFormateado);

                // Confirmamos al remitente que el mensaje fue enviado
                salida.println(nombreUsuario + "--> " + mensaje);
            }

        } catch (IOException e) {
            System.err.println("Error con el cliente " + nombreUsuario + ": " + e.getMessage());
        } finally {
            desconectar();
        }
    }

    /** Envia un mensaje a ESTE cliente (llamado desde otro ClienteHandler) */
    public void enviarMensaje(String mensaje) {
        if (salida != null) {
            salida.println(mensaje);
        }
    }

    /** Construye y envia la lista de usuarios actualmente conectados */
    private void enviarListaUsuarios() {
        List<String> usuarios = ChatServer.getUsuariosConectados();
        salida.println("--- Usuarios conectados ---");
        for (String u : usuarios) {
            salida.println("  * " + u);
        }
        salida.println("---------------------------");
    }

    /** Cierra la conexion y elimina al usuario del mapa global */
    private void desconectar() {
        if (nombreUsuario != null) {
            ChatServer.desconectarUsuario(nombreUsuario);
        }
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            System.err.println("Error al cerrar socket: " + e.getMessage());
        }
    }
}
