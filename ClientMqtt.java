import java.net.*;
import java.io.*;


public class ClientMqtt {
	private String hostname;
	private int port;
	private String userName;

	public ClientMqtt(String hostname, int port) {
		this.hostname = "127.0.0.1";
		this.port = 8080;
	}

	public void execute() {
		try {
			Socket socket = new Socket(hostname, port);

			System.out.println("Connected to the server");

			new ReadThread(socket, this).start();
			new WriteThread(socket, this).start();

		} catch (UnknownHostException ex) {
			System.out.println("Server not found: " + ex.getMessage());
		} catch (IOException ex) {
			System.out.println("I/O Error: " + ex.getMessage());
		}

	}

	void setUserName(String userName) {
		this.userName = userName;
	}

	String getUserName() {
		return this.userName;
	}


	public static void main(String[] args) {
		if (args.length < 2) return;

		String hostname = args[0];
		int port = Integer.parseInt(args[1]);

		ClientMqtt client = new ClientMqtt(hostname, port);
		client.execute();
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
			System.out.println("Error getting input stream: " + ex.getMessage());
			ex.printStackTrace();
		}
	}

	public void run() {
		while (true) {
			try {
				String response = reader.readLine();
				System.out.println("\n" + response);

				// prints the username after displaying the server's message
				if (client.getUserName() != null) {
					System.out.print("[" + client.getUserName() + "]: ");
				}
			} catch (IOException ex) {
				System.out.println("Error reading from server: " + ex.getMessage());
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
			System.out.println("Error getting output stream: " + ex.getMessage());
			ex.printStackTrace();
		}
	}

	public void run() {
		Console console = System.console();
		String text;
		client.setUserName("thang");
				
		try {
			String signincode = console.readLine();
		   	os.write(signincode);
			os.flush();  
			
			do {
				text = console.readLine(client.getUserName());
				os.write(text);
				os.newLine();
				os.flush();  

			} while (!text.equals("bye"));
		} catch (IOException ex) {
			System.out.println("Error reading from server: " + ex.getMessage());
			ex.printStackTrace();
		}
	}
}
