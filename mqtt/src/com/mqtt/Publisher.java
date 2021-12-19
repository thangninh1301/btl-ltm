package com.mqtt;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.net.*;
import java.io.*;
import java.util.Objects;

public class Publisher {
    private JButton startButton;
    private JLabel port;
    private JTextField tTopic;
    private JTextField tPort;
    private JTextField input;
    private JButton sendButton;
    private JPanel panelMain;
    private JTextPane output;
    private JRadioButton publisherRadioButton;
    private JRadioButton subscriberRadioButton;
    private ClientMqtt clientMqtt = new ClientMqtt();
    private String[] parts;
    public Publisher() {
        startButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (subscriberRadioButton.isSelected() == publisherRadioButton.isSelected()) {
                    JOptionPane.showMessageDialog(null, "select sub/pub first");
                    return;
                }

                if (Objects.equals(tPort.getText(), "")) {
                    JOptionPane.showMessageDialog(null, "input port first");
                    return;
                }

                if (Objects.equals(tTopic.getText(), "")) {
                    JOptionPane.showMessageDialog(null, "input topic first");
                    return;
                }

                if (subscriberRadioButton.isSelected()) {
                    sendButton.setVisible(false);
                    clientMqtt.setPublisher(false);
                }

                parts = tTopic.getText().split("/");

                if (parts[1].length() == 0 || parts[2].length() == 0) {
                    JOptionPane.showMessageDialog(null, "invalid topic");
                    return;
                }

                int length = parts[1].length();
                for (int i = 0; i < 16 - length; i++) {
                    parts[1]+=' ';
                }

                clientMqtt.setTopic(parts[1] + parts[2]);
                clientMqtt.setPort(Integer.parseInt(tPort.getText()));
                clientMqtt.setOutput(output);
                clientMqtt.setInput(input);
                clientMqtt.start();
            }
        });
        sendButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (!Objects.equals(input.getText(), "")) {
                    clientMqtt.setSending(true);
                }
            }
        });

        tPort.addKeyListener(new KeyAdapter() {
            @Override
            public void keyTyped(KeyEvent e) {
                char c = e.getKeyChar();
                if (!((c >= '0') && (c <= '9') ||
                        (c == KeyEvent.VK_BACK_SPACE) ||
                        (c == KeyEvent.VK_DELETE))) {
                    e.consume();
                }
            }
        });

    }

    public static void main(String[] args) {
        JFrame frame = new JFrame("publisher");
        frame.setContentPane(new Publisher().panelMain);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setPreferredSize(new Dimension(400, 300));
        frame.pack();
        frame.setVisible(true);
    }
}

class ReadThread extends Thread {
    private BufferedReader reader;
    private Socket socket;
    private ClientMqtt client;

    public ReadThread(Socket socket, ClientMqtt client) {
        this.socket = socket;
        this.client = client;

        try {
            InputStream input = socket.getInputStream();
            reader = new BufferedReader(new InputStreamReader(input));
        } catch (IOException ex) {
            client.newOutput("Error getting input: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    public void run() {
        while (true) {
            try {
                String response = reader.readLine();
                client.newOutput(response);
            } catch (IOException ex) {
                client.newOutput("Error reading from server: " + ex.getMessage());
                ex.printStackTrace();
                break;
            }
        }
    }
}

class WriteThread extends Thread {
    private BufferedWriter os;
    private Socket socket;
    private ClientMqtt client;

    public WriteThread(Socket socket, ClientMqtt client) {
        this.socket = socket;
        this.client = client;

        try {
            OutputStream output = socket.getOutputStream();
            os = new BufferedWriter(new OutputStreamWriter(output));
        } catch (IOException ex) {
            client.newOutput("Error getting output: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    public void run() {
        try {
            String signInCode;
            if (client.isPublisher()) {
                signInCode = "1" + client.getTopic();
            } else {
                signInCode = "0" + client.getTopic();
            }
            os.write(signInCode);
            os.flush();

            do {
                if (client.getSending()) {
                    if (!Objects.equals(client.getInput().getText(), "")) {
                        client.newOutput(client.getInput().getText());
                        os.write(client.getInput().getText());
                        os.newLine();
                        os.flush();
                        client.getInput().setText("");
                    }
                    client.setSending(false);
                }
                sleep(200);
            } while (true);
        } catch (IOException ex) {
            client.newOutput("Error reading from server: " + ex.getMessage());
            ex.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}

class ClientMqtt {
    private String hostname = "127.0.0.1";
    private int port = 8080;
    private String topic;
    private JTextPane output;
    private JTextField input;
    private Boolean publisher = true;
    private Boolean sending = false;

    void setPort(int value) {
        this.port = value;
    }

    void setTopic(String value) {
        this.topic = value;
    }

    String getTopic() {
        return this.topic;
    }

    void setInput(JTextField input) {
        this.input = input;
    }

    JTextField getInput() {
        return this.input;
    }

    void setSending(Boolean value) {
        this.sending = value;
    }

    Boolean getSending() {
        return this.sending;
    }

    void setPublisher(Boolean publisher) {
        this.publisher = publisher;
    }

    Boolean isPublisher() {
        return this.publisher;
    }

    public void newOutput(String content) {
        output.setText(output.getText() + content + "\n");
    }

    void setOutput(JTextPane output) {
        this.output = output;
    }

    public void start() {
        try {
            Socket socket = new Socket(hostname, port);
            newOutput("Connected to the broker server");

            new ReadThread(socket, this).start();
            new WriteThread(socket, this).start();

        } catch (UnknownHostException ex) {
            newOutput("Server not found: " + ex.getMessage());
        } catch (IOException ex) {
            newOutput("Error: " + ex.getMessage());
        }

    }
}