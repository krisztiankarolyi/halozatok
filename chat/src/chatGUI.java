import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import javax.json.*;


public class chatGUI  extends JFrame{
    private String userName = "";
    private JTextField messageField;
    private JTextArea chatArea;
    private Socket clientSocket;


    public chatGUI(String host, int port) {
        initUI();
        startClient(host, port);
    }

    private void initUI() {
        setTitle("Chat Client");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(500, 400);
        setLocationRelativeTo(null);

        messageField = new JTextField();
        chatArea = new JTextArea();
        chatArea.setEditable(false);

        JButton sendButton = new JButton("Send");
        sendButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String message = messageField.getText().toString();
                if(message.toLowerCase().startsWith("@private"))
                {
                    String[] parts = message.split(" ", 4);

                    if (parts.length == 4) {
                        String receiver = parts[1];
                        String content = parts[3];
                        appendToChatArea(String.format("[Privát]: Te --> %s: %s", receiver, content));
                        sendMessage(clientSocket, "private", content, userName, receiver);
                    }
                    else {
                        appendToChatArea("Hibás privát üzenet formátum. Használd a következőt: @private [címzett] -> [üzenet]");
                    }
                }

                else if(message.toLowerCase().equals("@help")){
                    printAvailableCommands();
                }

                else {
                    sendMessage(clientSocket, "public", message, userName, "all");
                }

                messageField.setText("");
            }
        });



        JScrollPane scrollPane = new JScrollPane(chatArea);

        GroupLayout layout = new GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setAutoCreateGaps(true);
        layout.setAutoCreateContainerGaps(true);

        layout.setHorizontalGroup(
                layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                        .addComponent(scrollPane)
                        .addGroup(layout.createSequentialGroup()
                                .addComponent(messageField)
                                .addComponent(sendButton))
        );

        layout.setVerticalGroup(
                layout.createSequentialGroup()
                        .addComponent(scrollPane)
                        .addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                                .addComponent(messageField)
                                .addComponent(sendButton))
        );


    }

    private void startClient(String host, int port) {
        try {
            clientSocket = new Socket(host, port);

            // Prompt the user for their name using a dialog

            while(userName.equals(""))
            {
                userName = JOptionPane.showInputDialog(this, "Kérjük, adja meg a nevét:", "Bejelentkezés", JOptionPane.PLAIN_MESSAGE);
            }
            sendMessage(clientSocket, "user_info", userName, userName, "server");

            Thread receiveThread = new Thread(() -> receiveMessages(clientSocket));
            receiveThread.start();


        } catch (IOException e) {
            appendToChatArea("A kapcsolat megszakadt.");
            e.printStackTrace();
        }
    }


    private void receiveMessages(Socket clientSocket) {
        try {
            BufferedInputStream inputStream = new BufferedInputStream(clientSocket.getInputStream());

            while (true) {
                byte[] buffer = new byte[1024];
                int bytesRead = inputStream.read(buffer);

                if (bytesRead == -1) {
                    // The connection is lost
                    appendToChatArea("The connection is lost.");
                    break;
                }

                String jsonMessage = new String(buffer, 0, bytesRead, StandardCharsets.UTF_8);

                if ("@exit".equals(jsonMessage.trim())) {
                    appendToChatArea("The connection is lost.");
                    break;

                }

                // Update GUI with received message
                SwingUtilities.invokeLater(() -> processReceivedJsonMessage(jsonMessage));
            }
        } catch (IOException e) {
            appendToChatArea("Error receiving messages.");
            e.printStackTrace();
        }
    }

    private void processReceivedJsonMessage(String jsonMessage) {
        try (JsonReader reader = Json.createReader(new StringReader(jsonMessage))) {
            // JSON object creation from the received text
            JsonObject jsonObject = reader.readObject();
            String messageType = jsonObject.getString("message_type");
            String sender = jsonObject.getString("sender");
            String content = jsonObject.getString("content");
            String time = jsonObject.getString("timestamp");

            if ("server".equals(messageType)) {
                // Server message
                appendToChatArea("[SZERVER] | " + time + ": " + content  );
            } else if ("public".equals(messageType)) {
                // Public message
                appendToChatArea("<" + sender + "> | " + time + ": " + content);
            } else if ("private".equals(messageType)) {
                // Private message
                appendToChatArea("[Privát] küldte: <" + sender + "> | " + time + ": " + content );
            }
        } catch (Exception e) {
            appendToChatArea("Hiba a JSON üzenet feldolgozása során.");
            e.printStackTrace();
        }
    }


    private String readLine(BufferedReader reader) throws IOException {
        return reader.readLine();
    }

    public  void printAvailableCommands() {
        appendToChatArea("Elérhető parancsok:");
        appendToChatArea("@exit - Kilépés a chatről+ \n @help - Elérhető parancsok listázása \n @users - Aktív felhasználók listázása \n \"@newName [új név] - Felhasználónév megváltoztatása\"");
    }
    // Add this method to append messages to the chatArea
    private void appendToChatArea(String message) {
        chatArea.append(message + "\n");
    }


    private void sendMessage(Socket clientSocket, String messageType, String content, String sender, String receiver) {
        try {
            String timestamp = new SimpleDateFormat("HH:mm:ss").format(new Date());
            String message = String.format("{\"message_type\":\"%s\",\"content\":\"%s\",\"timestamp\":\"%s\",\"sender\":\"%s\",\"receiver\":\"%s\"}",
                    messageType, content, timestamp, sender, receiver);

            PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
            out.println(message);

            // Clear the messageField after sending the message
            messageField.setText("");

        } catch (IOException e) {
            appendToChatArea("Hiba az üzenet küldése során.");
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                String ip = JOptionPane.showInputDialog("Kérem adja meg az IP-címet:");
                String portStr = JOptionPane.showInputDialog("Kérem adja meg a portot:");
                int port = Integer.parseInt(portStr);

                new chatGUI(ip, port).setVisible(true);
            }
        });
    }

}
