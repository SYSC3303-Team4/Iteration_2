// TFTPServer.java
// This class is the server side of a simple TFTP server based on
// UDP/IP. The server receives a read or write packet from a client and
// sends back the appropriate response without any actual file transfer.
// One socket (69) is used to receive (it stays open) and another for each response. 

import java.io.*; 
import java.net.*;
import java.util.*;

import javax.swing.BorderFactory;
import javax.swing.JFileChooser;
import javax.swing.JFrame;

import javax.swing.JScrollPane;
import javax.swing.JTextArea;

import ui.ConsoleUI;

public class TFTPServer{ 

	// types of requests we can receive
	public static enum Request { READ, WRITE, ERROR};
	// responses for valid requests
	public static final byte[] readResp = {0, 3, 0, 1};
	public static final byte[] writeResp = {0, 4, 0, 0};

	// UDP datagram packets and sockets used to send / receive
	private DatagramPacket sendPacket, receivePacket;
	private DatagramSocket receiveSocket, sendSocket;
	private static boolean verbose = false;
	private static Scanner scan= new Scanner(System.in);
	private ConsoleUI console;
    private JTextArea fileChooserFrame;
	private File file;
	private JFileChooser fileChooser;
    private String path= "DEFAULT_TEST_WRITE";

	/**
	 * JTextArea for the thread executing main().
	 */
	private JTextArea status;

	private JTextArea commandLine;

	/**
	 * Build the GUI.
	 */

	public TFTPServer(String title)
	{

		//make and run the UI
		console = new ConsoleUI(title);
		console.run();

		try {
			// Construct a datagram socket and bind it to port 69
			// on the local host machine. This socket will be used to
			// receive UDP Datagram packets.
			receiveSocket = new DatagramSocket(69);
		} catch (SocketException se) {
			console.print("SOCKET BIND ERROR");
			se.printStackTrace();
			System.exit(1);
		}
        fileChooserFrame = new JTextArea(5,40);
		fileChooser = new JFileChooser();
		fileChooser.setCurrentDirectory(new File(System.getProperty("user.home")));
		fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		int result = fileChooser.showOpenDialog(fileChooser);
		if (result == JFileChooser.APPROVE_OPTION) {//file is found
		    file = fileChooser.getSelectedFile();//get file name
		}
	}
	

	public void receiveAndSendTFTP() throws Exception
	{
		// out.append("Initializing Server...\n");
		//Find whether you want to run in verbose mode or not


		byte[] data,
		response = new byte[4];

		Request req; // READ, WRITE or ERROR
		ArrayList currentThreads;
		String filename, mode;
		int len, j=0, k=0;
		int threadNum = 0;
		String input = "";
		boolean runFlag = true;
		ThreadGroup initializedThreads = new ThreadGroup("ServerThread");
		
		//check for initial input
		console.print("Please specify verbose mode true or false");
		input = console.getInput(true);
		input.toLowerCase();
		if (input.equals("true"))
		{
			verbose = true;
		}
		else
		{
			verbose = false;
		}
		
		//main input loop
		while(runFlag) 
		{
			input = console.getInput(false);
			if(input != null)
			{
				if(input.equals("close"))
				{
					runFlag = false;
					/* 
					Set<Thread> threadSet = Thread.getAllStackTraces().keySet();
					while(threadSet.iterator().hasNext()){
						Thread s = threadSet.iterator().next();
						if(s.getThreadGroup().getName().equals(initializedThreads.getName()))
						{
							((ServerThread)s).interrupt();
						}
					}
					*/
				}
				else
				{
					console.print("!UNKNOWN INPUT!");
				}
			}
			else
			{
					// loop forever
					// Construct a DatagramPacket for receiving packets up
					// to 100 bytes long (the length of the byte array).

					data = new byte[100];
					receivePacket = new DatagramPacket(data, data.length);

					console.print("Server: Waiting for packet.");
					// Block until a datagram packet is received from receiveSocket.
					try {
						receiveSocket.receive(receivePacket);
					}
					catch (IOException e) {
						e.printStackTrace();
						System.exit(1);
					}

					// Process the received datagram.
					console.print("Server: Packet received:");
					console.print("From host: " + receivePacket.getAddress());
					console.print("Host port: " + receivePacket.getPort());
					len = receivePacket.getLength();
					console.print("Length: " + len);

					int packetSize = receivePacket.getLength();

					console.printByteArray(data, packetSize);
					console.printIndent("Cntn:  " + (new String(data,0,packetSize)));

					// Form a String from the byte array.
					String received = new String(data,0,len);
					console.print(received);

					// If it's a read, send back DATA (03) block 1
					// If it's a write, send back ACK (04) block 0
					// Otherwise, ignore it
					if (data[0]!=0) req = Request.ERROR; // bad
					else if (data[1]==1) req = Request.READ; // could be read
					else if (data[1]==2) req = Request.WRITE; // could be write
					else req = Request.ERROR; // bad

					if (req!=Request.ERROR) { // check for filename
						// search for next all 0 byte
						for(j=2;j<len;j++) {
							if (data[j] == 0) break;
						}
						if (j==len) req=Request.ERROR; // didn't find a 0 byte
						if (j==2) req=Request.ERROR; // filename is 0 bytes long
						// otherwise, extract filename
						filename = new String(data,2,j-2);
					}

					if(req!=Request.ERROR) { // check for mode
						// search for next all 0 byte
						for(k=j+1;k<len;k++) { 
							if (data[k] == 0) break;
						}
						if (k==len) req=Request.ERROR; // didn't find a 0 byte
						if (k==j+1) req=Request.ERROR; // mode is 0 bytes long
						mode = new String(data,j,k-j-1);
					}

					if(k!=len-1) req=Request.ERROR; // other stuff at end of packet        

					// Create a response.
					if (req==Request.READ) { // for Read it's 0301
						threadNum++;
						Thread readRequest =  new TFTPReadThread(initializedThreads,console, receivePacket, "Thread "+threadNum, verbose);
						readRequest.start();
						response = readResp;
					} else if (req==Request.WRITE) { // for Write it's 0400
						threadNum++;
						Thread writeRequest =  new TFTPWriteThread(initializedThreads,console, receivePacket,"Thread "+threadNum, verbose,file);
						writeRequest.start();
						response = writeResp;
					} else { // it was invalid, just quit
						throw new Exception("Not yet implemented");
					} 
				}
			}
			System.exit(0);

		} 

	Thread[] getServerThreads( final ThreadGroup group ) {
		if ( group == null )
			throw new NullPointerException( "Null thread group" );
		int nAlloc = group.activeCount( );
		int n = 0;
		Thread[] threads;
		do {
			nAlloc *= 2;
			threads = new Thread[ nAlloc ];
			n = group.enumerate( threads );
		} while ( n == nAlloc );
		return java.util.Arrays.copyOf( threads, n );
	}

	public static void main( String args[] ) throws Exception
	{

		TFTPServer c = new TFTPServer("TFTP Server");
		c.receiveAndSendTFTP();
	}
}