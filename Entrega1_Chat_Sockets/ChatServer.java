package Entrega1_Chat_Sockets;

import java.io.*;
import java.net.*;
import java.util.*;

// Servidor del chat - espera conexiones de clientes y enruta los mensajes entre ellos
public class ChatServer {

    // Puerto donde escucha el servidor
    private static final int PUERTO = 59420;

    // Lista de usuarios conectados: nombre -> su hilo
    private static Map<String, ClienteHandler> usuariosConectados = new HashMap<>();

    public static void main(String[] args) {
        System.out.println("Servidor iniciado en el puerto: " + PUERTO);
        System.out.println("Esperando clientes...\n");

        try (ServerSocket serverSocket = new ServerSocket(PUERTO)) {
            // El servidor corre indefinidamente aceptando nuevos clientes
            while (true) {
                Socket socketCliente = serverSocket.accept();
                System.out.println("Nueva conexion desde: " + socketCliente.getInetAddress());

                // Cada cliente se maneja en su propio hilo para no bloquear a los demas
                ClienteHandler manejador = new ClienteHandler(socketCliente);
                new Thread(manejador).start();
            }
        } catch (IOException e) {
            System.err.println("Error en el servidor: " + e.getMessage());
        }
    }

    // Agrega un usuario a la lista de conectados
    public static synchronized void registrarUsuario(String nombre, ClienteHandler handler) {
        usuariosConectados.put(nombre, handler);
        System.out.println("Usuario \"" + nombre + "\" conectado. Total: " + usuariosConectados.size());
    }

    // Elimina un usuario cuando se desconecta
    public static synchronized void desconectarUsuario(String nombre) {
        usuariosConectados.remove(nombre);
        System.out.println("El usuario \"" + nombre + "\" abandono el chat.");
    }

    // Retorna la lista de nombres de usuarios activos
    public static synchronized List<String> getUsuariosConectados() {
        return new ArrayList<>(usuariosConectados.keySet());
    }

    // Busca el hilo de un usuario por su nombre
    public static synchronized ClienteHandler getHandler(String nombre) {
        return usuariosConectados.get(nombre);
    }
}


// Maneja la comunicacion con un cliente especifico en su propio hilo
class ClienteHandler implements Runnable {

    private Socket socket;
    private PrintWriter salida;
    private BufferedReader entrada;
    private String nombreUsuario;

    public ClienteHandler(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        try {
            entrada = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
            salida = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true);

            // Pedir nombre de usuario
            salida.println("Bienvenido al Chat del Politecnico. Ingresa tu nombre de usuario:");
            nombreUsuario = entrada.readLine();

            if (nombreUsuario == null || nombreUsuario.trim().isEmpty()) {
                salida.println("Nombre invalido. Desconectando.");
                socket.close();
                return;
            }

            nombreUsuario = nombreUsuario.trim();
            ChatServer.registrarUsuario(nombreUsuario, this);

            // Enviar lista de usuarios conectados
            enviarListaUsuarios();

            // El cliente elige con quien chatear
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

            salida.println("--- Chat iniciado con " + destinatario + ". Escribe 'chao' para salir. ---");
            handlerDestinatario.enviarMensaje("--- " + nombreUsuario + " quiere chatear contigo. ---");

            // Bucle de mensajes
            String mensaje;
            while ((mensaje = entrada.readLine()) != null) {
                if (mensaje.equalsIgnoreCase("chao")) {
                    handlerDestinatario.enviarMensaje("--- " + nombreUsuario + " abandono el chat (chao). ---");
                    salida.println("--- Has salido del chat. Hasta luego! ---");
                    break;
                }
                // Reenviar mensaje al destinatario
                handlerDestinatario.enviarMensaje(nombreUsuario + "--> " + mensaje);
                salida.println(nombreUsuario + "--> " + mensaje);
            }

        } catch (IOException e) {
            System.err.println("Error con el cliente " + nombreUsuario + ": " + e.getMessage());
        } finally {
            desconectar();
        }
    }

    // Envia un mensaje a este cliente
    public void enviarMensaje(String mensaje) {
        if (salida != null) {
            salida.println(mensaje);
        }
    }

    // Muestra la lista de usuarios conectados al cliente
    private void enviarListaUsuarios() {
        List<String> usuarios = ChatServer.getUsuariosConectados();
        salida.println("--- Usuarios conectados ---");
        for (String u : usuarios) {
            salida.println("  * " + u);
        }
        salida.println("---------------------------");
    }

    // Cierra la conexion y elimina al usuario del servidor
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
