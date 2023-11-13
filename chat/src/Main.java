import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import org.json.JSONObject;
import javax.json.*;

public class Main {

    private static String userName = "";

    public static void main(String[] args) {
        BufferedReader consoleInput = new BufferedReader(new InputStreamReader(System.in));

        System.out.println("Kérem a kiszolgáló IP címét:");
        String host = readLine(consoleInput);

        System.out.println("Kérem a portot:");
        int port = Integer.parseInt(readLine(consoleInput));

        startClient(host, port);
    }


    private static void receiveMessages(Socket clientSocket) {
        try {
            BufferedInputStream inputStream = new BufferedInputStream(clientSocket.getInputStream());

            while (true) {
                byte[] buffer = new byte[1024];
                int bytesRead = inputStream.read(buffer);

                if (bytesRead == -1) {
                    // A kapcsolat megszakadt
                    System.out.println("A kapcsolat megszakadt.");
                    break;
                }

                String jsonMessage = new String(buffer, 0, bytesRead, StandardCharsets.UTF_8);

                if ("@exit".equals(jsonMessage.trim())) {
                    System.out.println("A kapcsolat megszakadt.");
                    break;
                }

                processReceivedJsonMessage(jsonMessage);
            }
        } catch (IOException e) {
            System.out.println("Hiba az üzenet fogadása során.");
            e.printStackTrace();
        }
    }

    private static void processReceivedJsonMessage(String jsonMessage) {
        try (JsonReader reader = Json.createReader(new StringReader(jsonMessage))) {
            // JSON objektum létrehozása a fogadott szövegből
            JsonObject jsonObject = reader.readObject();
            String messageType = jsonObject.getString("message_type");
            String sender = jsonObject.getString("sender");
            String content = jsonObject.getString("content");
            String time = jsonObject.getString("timestamp");

            if ("server".equals(messageType)) {
                // Szerver üzenete
                System.out.println("\u001B[33m[SZERVER] | " + time + ": " + content + "  \u001B[0m"); // sárgára színezve
            } else if ("public".equals(messageType)) {
                // Közönséges üzenet
                System.out.println("<" + sender + "> | " + time + ": " + content);
            } else if ("private".equals(messageType)) {
                // Privát üzenet
                System.out.println("\u001B[31m[Privát] küldte: <" + sender + "> | " + time + ": " + content + "\u001B[0m"); // piros színnel
            }


        } catch (Exception e) {
            System.out.println("Hiba a JSON üzenet feldolgozása során.");
            e.printStackTrace();
        }
    }


    private static void startClient(String host, int port) {
        try {
            Socket clientSocket = new Socket(host, port);

            System.out.println("\u001B[34mKérjük, adja meg a nevét:\u001B[0m");
            userName = readLine(new BufferedReader(new InputStreamReader(System.in)));
            sendMessage(clientSocket, "user_info", userName, userName, "server");

            Thread receiveThread = new Thread(() -> receiveMessages(clientSocket));
            Thread sendThread = new Thread(() -> sendMessages(clientSocket));

            receiveThread.start();
            sendThread.start();

        } catch (IOException e) {
            System.out.println("A kapcsolata megszakadt.");
            e.printStackTrace();
        }
    }

    private static void printAvailableCommands() {
        System.out.println("Elérhető parancsok:");
        System.out.println("@exit - Kilépés a chatről");
        System.out.println("@help - Elérhető parancsok listázása");
        System.out.println("@private [címzett] -> [üzenet] - Privát üzenet küldése");
        System.out.println("@users - Aktív felhasználók listázása");
        System.out.println("@newName [új név] - Felhasználónév megváltoztatása");
    }

    private static void sendMessage(Socket clientSocket, String messageType, String content, String sender, String receiver) {
        try {
            String timestamp = new SimpleDateFormat("HH:mm:ss").format(new Date());
            String message = String.format("{\"message_type\":\"%s\",\"content\":\"%s\",\"timestamp\":\"%s\",\"sender\":\"%s\",\"receiver\":\"%s\"}",
                    messageType, content, timestamp, sender, receiver);

            PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
            out.println(message);

        } catch (IOException e) {
            System.out.println("Hiba az üzenet küldése során.");
            e.printStackTrace();
        }
    }



    private static String extractValue(String part) {
        return part.split(":")[1].replace(",", "").replace("\"", "").trim();
    }

    private static void sendMessages(Socket clientSocket) {
        try {
            BufferedReader consoleInput = new BufferedReader(new InputStreamReader(System.in));

            while (true) {
                String message = readLine(consoleInput);
                if (message.toLowerCase().equals("@exit")) {
                    sendMessage(clientSocket, "server", "@exit", userName, "all");
                    clientSocket.close();
                    System.out.println("\u001B[34mKiléptél a chat-ből.\u001B[0m");
                    break;
                } else if (message.toLowerCase().equals("@help")) {
                    printAvailableCommands();
                } else if (message.startsWith("@private")) {
                    String[] parts = message.split(" ", 4);
                    if (parts.length == 4) {
                        String receiver = parts[1];
                        String content = parts[3];
                        System.out.printf("\u001B[34m[Privát]: Te --> %s: %s\u001B[0m%n", receiver, content);
                        sendMessage(clientSocket, "private", content, userName, receiver);
                    } else {
                        System.out.println("Hibás privát üzenet formátum. Használd a következőt: @private [címzett] -> [üzenet]");
                    }
                } else {
                    sendMessage(clientSocket, "public", message, userName, "all");
                }
            }
        } catch (IOException e) {
            System.out.println("Hiba az üzenet küldése során.");
            e.printStackTrace();
        }
    }

    private static String readLine(BufferedReader reader) {
        try {
            return reader.readLine();
        } catch (IOException e) {
            System.out.println("Hiba az adat beolvasása során.");
            e.printStackTrace();
            return "";
        }
    }
}
