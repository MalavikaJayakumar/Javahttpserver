package malri;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.sql.*;
import java.util.Date;
import java.util.Scanner;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;

// The tutorial can be found just here on the SSaurel's Blog : 
// https://www.ssaurel.com/blog/create-a-simple-http-web-server-in-java
// Each Client Connection will be managed in a dedicated Thread
public class MalRiServer implements Runnable{ 
	
	static final File WEB_ROOT = new File("/home/stas/programming/java/gui/webServer/src/mal");
	static final String DEFAULT_FILE = "luvia1.html";
	static final String FILE_NOT_FOUND = "404.html";
	static final String METHOD_NOT_SUPPORTED = "not_supported.html";
	// port to listen connection
	static final int PORT = 8080;
        static final String url = "jdbc:mysql://localhost:3306/fisher";
        static final String username = "fish";
        static final String password = "stas";
        static  Connection con = null;
        static  Statement st = null;
        static  ResultSet rs = null;
        static  String aUser=null;
	
	// verbose mode
	static final boolean verbose = true;
	
	// Client Connection via Socket Class  
	private Socket connect;
	
	public MalRiServer(Socket c) {
		connect = c;
	}
	
	public static void main(String[] args) {
		try {
			ServerSocket serverConnect = new ServerSocket(PORT);
			System.out.println("Server started.\nListening for connections on port : " + PORT + " ...\n");
            
                        
			// we listen until user halts server execution
			while (true) {
				MalRiServer myServer = new MalRiServer(serverConnect.accept());
				
				if (verbose) {
					System.out.println("Connecton opened. (" + new Date() + ")");
				}
				
				// create dedicated thread to manage the client connection
				Thread thread = new Thread(myServer);
				thread.start();
			}                   
			
		} catch (IOException e) {
			System.err.println("Server Connection error : " + e.getMessage());
		}
	}

	@Override
	public void run() {
		// we manage our particular client connection
		BufferedReader in = null; PrintWriter out = null; BufferedOutputStream dataOut = null;
		String fileRequested = null;
                
		
		try {
			// we read characters from the client via input stream on the socket
			in = new BufferedReader(new InputStreamReader(connect.getInputStream()));
			// we get character output stream to client (for headers)
			out = new PrintWriter(connect.getOutputStream());
			// get binary output stream to client (for requested data)
			dataOut = new BufferedOutputStream(connect.getOutputStream());
			
			// get first line of the request from the client
			String input = in.readLine();
			// we parse the request with a string tokenizer
			StringTokenizer parse = new StringTokenizer(input);
			String method = parse.nextToken().toUpperCase(); // we get the HTTP method of the client
			// we get file requested
			fileRequested = parse.nextToken().toLowerCase();
			System.out.println("\n\n"+fileRequested);
			// we support only GET and HEAD methods, we check
			if (!method.equals("GET")  &&  !method.equals("HEAD")) {
				if (verbose) {
					System.out.println("501 Not Implemented : " + method + " method.");
				}
				
				// we return the not supported file to the client
				File file = new File(WEB_ROOT, METHOD_NOT_SUPPORTED);
				int fileLength = (int) file.length();
				String contentMimeType = "text/html";
				//read content to return to client
				byte[] fileData = readFileData(file, fileLength);
				// we send HTTP Headers with data to client
				out.println("HTTP/1.1 501 Not Implemented");
				out.println("Server: Java HTTP Server from SSaurel : 1.0");
				out.println("Date: " + new Date());
				out.println("Content-type: " + contentMimeType);
				out.println("Content-length: " + fileLength);
				out.println(); // blank line between headers and content, very important !
				out.flush(); // flush character output stream buffer
				// file
				dataOut.write(fileData, 0, fileLength);
				dataOut.flush();
				//read content to return to client
				fileData = DataConect();
				fileLength = (int) fileData.length;
                                // we send HTTP Headers with data to client
				out.println("HTTP/1.1 501 Not Implemented");
				out.println("Server: Java HTTP Server from SSaurel : 1.0");
				out.println("Date: " + new Date());
				out.println("Content-type: " + contentMimeType);
				out.println("Content-length: " + fileLength);
				out.println(); // blank line between headers and content, very important !
				out.flush(); // flush character output stream buffer
				// file
				dataOut.write(fileData, 0, fileLength);
				dataOut.flush();
				
			} else {
				// GET or HEAD method
				if (fileRequested.endsWith("/")) {
					fileRequested += DEFAULT_FILE;
				}
                             
				
				File file = new File(WEB_ROOT, fileRequested);
				int fileLength = (int) file.length();
				String content = getContentType(fileRequested);
				
				if (method.equals("GET")) { // GET method so we return content
					byte[] fileData = readFileData(file, fileLength);
					
					// send HTTP Headers
					out.println("HTTP/1.1 200 OK");
					out.println("Server: Java HTTP Server from SSaurel : 1.0");
					out.println("Date: " + new Date());
					out.println("Content-type: " + "text/html");
					out.println("Content-length: " + fileLength);
					out.println(); // blank line between headers and content, very important !
					out.flush(); // flush character output stream buffer
                                        		
                                        Files.copy(file.toPath(), dataOut);
					//dataOut.write(fileData, 0, fileLength);
                                        dataOut.flush();                                         
				}
				
				if (verbose) {
					System.out.println("File " + fileRequested + " of type " + content + " returned");
				}
				
			}
			
		} catch (FileNotFoundException fnfe) {
			try {
				fileNotFound(out, dataOut, fileRequested);
			} catch (IOException ioe) {
				System.err.println("Error with file not found exception : " + ioe.getMessage());
			}
			
		} catch (IOException ioe) {
			System.err.println("Server error : " + ioe);
		} finally {
			try {
				in.close();
				out.close();
				dataOut.close();
				connect.close(); // we close socket connection
			} catch (Exception e) {
				System.err.println("Error closing stream : " + e.getMessage());
			} 
			
			if (verbose) {
				System.out.println("Connection closed.\n");
			}
		}
		
		
	}
	
	private byte[] readFileData(File file, int fileLength) throws IOException {
		FileInputStream fileIn = null;
		byte[] fileData = new byte[fileLength];
		
		try {
			fileIn = new FileInputStream(file);
			fileIn.read(fileData);
		} finally {
			if (fileIn != null) 
				fileIn.close();
		}
		return fileData;
	}
	
	// return supported MIME Types
	private String getContentType(String fileRequested) {
		if (fileRequested.endsWith(".htm")  ||  fileRequested.endsWith(".html"))
			return "text/html";
		else
                    if (fileRequested.endsWith(".jpg")  ||  fileRequested.endsWith(".gif")||  fileRequested.endsWith(".gif"))
			return "image/gif";
                else
			return "text/plain";
	}
	
	private void fileNotFound(PrintWriter out, OutputStream dataOut, String fileRequested) throws IOException {
                String content = "text/html";
		try {
                con = DriverManager.getConnection(url, username, password);
                st = con.createStatement();
                System.out.println("DBconnect OK");
                int id;
    String admName=null;
    fileRequested=fileRequested.substring(1);
    aUser="<html><Title>Malu Riya </Title> <Body> ";
    String passw=null;
    String query="SELECT * FROM Malu WHERE fName =\""+fileRequested+"\";";
    System.out.println(query);
    rs = st.executeQuery(query);
    System.out.println("Database connected!"+fileRequested);
    while (rs.next()) {//get first result
        id=rs.getInt("id");
        admName=rs.getString("fHead");
        passw=rs.getString("fData");
       String line ="<br> ";
        if (passw!=null){
        Scanner scanner = new Scanner(passw);
        while (scanner.hasNextLine()) {
        line =line+ scanner.nextLine()+" <br> ";
            // process the line
            }
        scanner.close();
        }
        else{
            line="<h1> Not in this server,/h1>";
        }
        aUser=aUser+"<br><h1>"+admName+"</h1></br>"+line;
          System.out.println(aUser);//coloumn 1
    }
    rs.close();
    st.close();
    con.close();
    aUser= aUser+" </body></html>";
    byte[] aData=aUser.getBytes();
    // send HTTP Headers
					out.println("HTTP/1.1 200 OK");
					out.println("Server: Java HTTP Server from SSaurel : 1.0");
					out.println("Date: " + new Date());
					out.println("Content-type: " + content);
					out.println("Content-length: " + aData.length);
					out.println(); // blank line between headers and content, very important !
					out.flush(); // flush character output stream buffer
                                        dataOut.write(aData, 0, aData.length);
                                        dataOut.flush();
            } catch (SQLException ex) {
                Logger.getLogger(MalRiServer.class.getName()).log(Level.SEVERE, null, ex);
            
                File file = new File(WEB_ROOT, FILE_NOT_FOUND);
		int fileLength = (int) file.length();
		byte[] fileData = readFileData(file, fileLength);
		
		out.println("HTTP/1.1 404 File Not Found");
		out.println("Server: Java HTTP Server from SSaurel : 1.0");
		out.println("Date: " + new Date());
		out.println("Content-type: " + content);
		out.println("Content-length: " + fileLength);
		out.println(); // blank line between headers and content, very important !
		out.flush(); // flush character output stream buffer
		
		dataOut.write(fileData, 0, fileLength);
		dataOut.flush();
		
		if (verbose) {
			System.out.println("File " + fileRequested + " not found");
		}
	}
            }
            
                
        
        
   private byte[] DataConect() {
       System.out.println("asfsadfsadf");
       byte[] aData=null;
            try ( Connection con1 = DriverManager.getConnection(url, username, password)) {
    st = con1.createStatement();
    int id;
    String admName=null;
    String passw=null;
    rs = st.executeQuery("SELECT * FROM admin;");
    System.out.println("Database connected!");
    while (rs.next()) {//get first result
        id=rs.getInt("id");
        admName=rs.getString("uname");
        passw=rs.getString("pwd");
         //   System.out.println(id+admName+passw);//coloumn 1
        aUser=aUser+admName;
    }
    rs.close();
    st.close();
    con1.close();
    aData= aUser.getBytes();
    
} catch (SQLException e) {
    throw new IllegalStateException("Cannot connect the database!", e);
}
            return aData;
        }
	
}