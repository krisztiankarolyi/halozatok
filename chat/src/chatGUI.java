import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import javax.json.*;
import net.miginfocom.swing.MigLayout;

public class chatGUI  extends JFrame{

    private DefaultListModel<String> userListModel;
    private JList<String> userList;


    private String userName = "";
    private JTextField messageField;
    private JTextArea chatArea;
    private Socket clientSocket;
    private Font font = new Font("MONOSPACED", Font.PLAIN, 16);


    public chatGUI(String host, int port) {
        initUI();
        startClient(host, port);
        SwingUtilities.invokeLater(this::requestUserList);
    }

    private void initUI() {
        setTitle("Chat Client");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(800, 600);
        setLocationRelativeTo(null);
        getContentPane().setBackground(Color.DARK_GRAY);
        getContentPane().setForeground(Color.WHITE);
        getContentPane().setFont(font);

        messageField = new JTextField();
        chatArea = new JTextArea();
        chatArea.setEditable(false);

        messageField.setBackground(Color.DARK_GRAY);
        chatArea.setBackground(Color.DARK_GRAY);
        messageField.setForeground(Color.WHITE);
        chatArea.setForeground(Color.WHITE);
        chatArea.setFont(font);
        messageField.setFont(font);

        JButton sendButton = new JButton("Send");
        sendButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if(!messageField.getText().trim().equals(""))
                    sendMessageFromTextField();
            }
        });

        messageField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    if(!messageField.getText().trim().equals(""))
                         sendMessageFromTextField();
                }
            }
        });

        userListModel = new DefaultListModel<>();
        userList = new JList<>(userListModel);
        userList.setBackground(Color.DARK_GRAY);
        userList.setForeground(Color.white);
        userList.setFont(font);

        JScrollPane userListScrollPane = new JScrollPane(userList);

        JButton refreshButton = new JButton("Refresh");
        refreshButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                requestUserList();
            }
        });

        userList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 1) {
                    JList<String> list = (JList<String>) e.getSource();
                    String selectedUser = list.getSelectedValue();
                    if (selectedUser != null) {
                        messageField.setText(String.format("@private %s -> ", (selectedUser).trim()));
                    }
                }
            }
        });

        setLayout(new MigLayout("fill", "[75%][25%]", "[75%, grow][25%, grow]"));

        add(new JScrollPane(chatArea), "cell 0 0, grow");
        add(userListScrollPane, "cell 1 0, grow");
        add(messageField, "cell 0 1, grow");
        add(sendButton, "cell 1 1, grow");
        add(refreshButton, "cell 1 1, grow");

    }

    private  void requestUserList() {
        sendMessage(clientSocket, "server", "@users", userName, "server");
    }

    private void updateUsersList(String users) {
        SwingUtilities.invokeLater(() -> {
            userListModel.clear();
            String[] userArray = users.split(":")[1].split(",");
            for (String user : userArray) {
                userListModel.addElement(user);
            }
        });
    }

    private void startClient(String host, int port) {
        try {
            clientSocket = new Socket(host, port);

            while(userName.replace(" ", "").equals(""))
            {
                userName = JOptionPane.showInputDialog(this, "Kérjük, adja meg a nevét:", "Bejelentkezés", JOptionPane.PLAIN_MESSAGE);
            }
            sendMessage(clientSocket, "user_info", userName, userName, "server");

            Thread receiveThread = new Thread(() -> receiveMessages(clientSocket));
            receiveThread.start();


        } catch (IOException e) {
            appendToChatArea("A kapcsolat megszakadt / sikertelen kapcsolódás.");
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
                    appendToChatArea("A kapcsolat megszakadt / sikertelen kapcsolódás.");
                    break;
                }

                String jsonMessage = new String(buffer, 0, bytesRead, StandardCharsets.UTF_8);

                if ("@exit".equals(jsonMessage.trim())) {
                    appendToChatArea("Kiléptél a chatből.");
                    System.exit(0);
                    break;
                }
                SwingUtilities.invokeLater(() -> processReceivedJsonMessage(jsonMessage));
            }
        } catch (IOException e) {
            appendToChatArea("Hiba az üzenetek fogadása közben.");
            e.printStackTrace();
        }
    }

    private void processReceivedJsonMessage(String jsonMessage) {
        try (JsonReader reader = Json.createReader(new StringReader(jsonMessage))) {
            JsonObject jsonObject = reader.readObject();
            String messageType = jsonObject.getString("message_type");
            String sender = jsonObject.getString("sender");
            String content = jsonObject.getString("content");
            String time = jsonObject.getString("timestamp");

            if ("server".equals(messageType)) {

                if(content.contains("Aktív")) {
                    updateUsersList(content);
                }

                else if(content.contains("csatlakozott")) {
                    requestUserList();
                    appendToChatArea("[SZERVER] | " + time + ": " + content);
                }
                else if(content.contains("kilépett")) {
                    requestUserList();
                }
                else if(content.contains("megváltozott")) {
                    requestUserList();
                    appendToChatArea("[SZERVER] | " + time + ": " + content);
                }
                else {
                    appendToChatArea("[SZERVER] | " + time + ": " + content);
                }

            }
            else if ("public".equals(messageType))
            {
                appendToChatArea("<" + sender + "> | " + time + ": " + content);
            }
            else if ("private".equals(messageType)) {
                appendToChatArea("[Privát] küldte: <" + sender + "> | " + time + ": " + content);
            }
        } catch (Exception e) {
            appendToChatArea("Hiba a JSON üzenet feldolgozása során.");
            e.printStackTrace();
        }
    }


    private void sendMessageFromTextField() {
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

    private String readLine(BufferedReader reader) throws IOException
    {
        return reader.readLine();
    }

    public  void printAvailableCommands()
    {
        appendToChatArea("Elérhető parancsok:");
        appendToChatArea("@exit - Kilépés a chatről+ \n @help - Elérhető parancsok listázása \n @users - Aktív felhasználók listázása \n \"@newName [új név] - Felhasználónév megváltoztatása\"");
    }

    private void appendToChatArea(String message) {

        chatArea.append(message + "\n");
    }

    private void sendMessage(Socket clientSocket, String messageType, String content, String sender, String receiver) {
        try {
            String timestamp = new SimpleDateFormat("HH:mm:ss").format(new Date());
            String message = String.format("{\"message_type\":\"%s\",\"content\":\"%s\",\"timestamp\":\"%s\",\"sender\":\"%s\",\"receiver\":\"%s\"}",
                    messageType, content, timestamp, sender, receiver);

            if(content.contains("@newName")){
                userName = content.split("@newName")[1];
            }
            PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
            out.println(message);
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
