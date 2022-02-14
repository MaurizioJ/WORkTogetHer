import java.io.BufferedReader;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.RemoteObject;
import java.rmi.server.UnicastRemoteObject;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Scanner;
import java.util.concurrent.ConcurrentHashMap;

public class ClientClass extends RemoteObject implements ClientInterface {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	BufferedReader input = null;
    ByteBuffer buffer = null;
    SocketChannel socketChannel = null;
    private boolean login; 
    private String flagUsername;
    private ConcurrentHashMap<String, String> statusClients;
    private ConcurrentHashMap<String, String> tabIp; 
    private ConcurrentHashMap<String, ArrayList<String>> chat; 
    private ConcurrentHashMap<String, ThreadMsg> tabThreads;
    
    public ClientClass() {
    	super();
    	chat = new ConcurrentHashMap<String, ArrayList<String>>();
    	tabIp = new ConcurrentHashMap<String,String>();
    	tabThreads= new ConcurrentHashMap<String, ThreadMsg> ();
    	flagUsername=null;
    	statusClients = null; 
    	login = false ; 
    }
    
	//implementazione metodo remoto dell'interfaccia del client 
	public void notifyEvent(ConcurrentHashMap<String,String> statusforClient)throws RemoteException{
		statusClients=statusforClient;
	}


	public synchronized void notifyProject(String progetto, String multicast) throws RemoteException{
		tabIp.put(progetto, multicast);
		chat.putIfAbsent(progetto, new ArrayList<String>());
		// attivo un thread per ogni progetto. Utile per memorizzare i messaggi in arrivo nell'hashmap chat
		ThreadMsg t =new ThreadMsg(chat.get(progetto),progetto,multicast);
		t.start(); 
		tabThreads.put(progetto, t);
	}
	

	public synchronized void notifyDeleteProject(String progetto)throws RemoteException{
		tabIp.remove(progetto);
		tabThreads.get(progetto).interrupt();
		try {
			tabThreads.get(progetto).join();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		tabThreads.remove(progetto);		
	}
	
	
	public void connect() {
				
		try {
			// 	RMI 

			Registry registry = LocateRegistry.getRegistry(5000);// cerco il registry alla porta 5000
			String nameServer = "Server"; 
			ServerInterface server = (ServerInterface) registry.lookup(nameServer); // ricavo lo stub del server
	
			// Registro la callback 
			ClientInterface stub = (ClientInterface) UnicastRemoteObject.exportObject(this,0);
			
		

			// Apro connessione TCP
			
	        try {
	            socketChannel = SocketChannel.open();
	            socketChannel.connect(new InetSocketAddress("localhost", 1919));
	            socketChannel.configureBlocking(true);
	            buffer = ByteBuffer.wrap(new byte[1024]);

	        }catch(IOException e){
	            e.printStackTrace();
	            System.exit(-1);
	        }
	        
			
			
	        
	        // funzione help() che stampa i comandi disponibili
	        help();
	        
	        server.sendStatusClient();
	        
	        /* SEZIONE COMANDI */
			System.out.println("Seleziona un comando o richiama la funzione HELP per consultare la lista dei comandi \n");
			boolean flag= false; // diventa true quando viene digitato il comando ESC
			Scanner scanner = new Scanner(System.in);

				while(!flag) {
					String [] arr = null;
					String line = null ;
					
					if (scanner.hasNext()) line =scanner.nextLine();
//					System.out.println("LINE = " + line);
					if(line!=null)  arr = line.split(" ",4);
						
					/*SEZIONE COMANDI */
					
					 if(arr[0].toUpperCase().equals("HELP")) {
						 help();
					 }
//					  REGISTER CLIENT 
					 else if(arr[0].toUpperCase().equals("REGISTER")) {
						 
							if(arr.length!=3) {
							 System.out.println("Si prega di utilizzare la sintassi dei comandi\n");
							 
							}
							
							else if(!login) {
								if(server.register(arr[1], arr[2])==1) {
								 System.out.println("Il Server ha registrato correttamente l'utente " + arr[1] + " \n");

								}
								else {
									System.out.println("Nome utente " + arr[1] + " già presente oppure c'è un errore nel login o password");
								}
							}
							else {
								 System.out.println("L'utente ha già effettuato il login ");
							 }
						 
						
						} // FINE REGISTER 
					
						 
					 
					 
					 //LOGIN CLIENT
					 else if(arr[0].toUpperCase().equals("LOGIN") ) {
						if(!login) {
							 if(arr.length!=3) {
								 System.out.println("Si prega di utilizzare la sintassi dei comandi\n");
								}
							 else { // login + user + pass
	    						 server.registerForCallback(arr[1], stub); // sottoscrivi al servizio di notifica
	
								 String message = channelCS(line); // ottengo la risposta da parte del server
								 String[] code= message.split(" "); 
						            if(code[0].equals("OK.")) { // risposta positiva server
	
						            	login=true;	
						            	flagUsername=arr[1].toLowerCase();
	
										 System.out.println("La risposta del Server è: " + message.toString());
						            }
				
						            else { // caso in cui non è loggato, ma sbaglia a inserire dati per login 
										 System.out.println("La risposta del Server è: " + message.toString());
		        						 server.deleteRegForCallback(arr[1].toLowerCase()); // disiscrivi al servizio di notifica
	
						            }
						          
							 }
						}	
						else {
							System.out.println("L'utente ha già effettuato il login! Bisogna prima disconnettersi");
						}
							
					 } // fine LOGIN
					 
					 //LOGOUT CLIENT
					 else if (arr[0].toUpperCase().equals("LOGOUT")) {
						synchronized(this) {
							 if(arr.length!=2) {
								 System.out.printf("Si prega di utilizzare la sintassi dei comandi\n");
							 }
							 else if(!login) {
								 System.out.printf("L'utente non ha ancora effettuato il login \n");
							 }
							 else if(!flagUsername.equals(arr[1].toLowerCase())) {
								 System.out.printf("Non si può effettuare il logout di un altro utente! \n");
							 }
	
							 else {
								 String message = channelCS(line); // ottengo la risposta da parte del server
								 String[] code= message.split(" "); 
								 if(code[0].equals("OK.")) {
									 login = false; 
									 flagUsername= null;
									 statusClients.clear(); // superfluo perché quando faccio la login, arriva un nuovo oggetto serializzato 
									 tabIp.clear();
									 chat.clear();
	        						 server.deleteRegForCallback(arr[1].toLowerCase()); // disiscrivi al servizio di notifica
	        						 closeAllChatThreads();
									 System.out.println("La risposta del Server è: " + message.toString());
								 }
								 else {
									 System.out.println("La risposta del Server è: " + message.toString());
		
								 }
							 }
						}
					 }// fine LOGOUT
					 
					//COMANDO ESC per terminare la connessione col server
					 else if(arr[0].toUpperCase().equals("ESC")) {
						 
						 if(arr.length!=1) { // ESC 
							 System.out.printf("Si prega di utilizzare la sintassi dei comandi\n");

						 }
						 else {
//							 System.out.print(login);
							 if(!login) {
								channelCS(line); // comunico al server che sto chiudendo la connessione
								login = false;
								flag=true; 
								scanner.close();
								System.out.println("Connessione col server chiusa");
							 }
//								
							 else {
								 System.out.print("Devi prima disconnettere l'utente \n");
							 }
						 }

					 } // FINE ESC
						
					// COMANDO LISTUSERS
					 else if(arr[0].toUpperCase().equals("LISTUSERS")){
						 if(arr.length!=1) {
							 System.out.printf("Si prega di utilizzare la sintassi dei comandi \n");
						 }
						 else if(!login){
							 System.out.printf("Utente non ha effettuato il login \n");
						 }
						 else if(statusClients==null) {
							System.out.printf("Lista utenti ancora vuota! \n");

						 }
						 else if(statusClients!=null) {
//							System.out.printf("mi aspetto la lista degli utenti \n");
							stampaUtenti();
						 }
						 
						
					 }
						// COMANDO LISTonlineUSERS
					 else if(arr[0].toUpperCase().equals("LISTONLINEUSERS")){
						 if(arr.length!=1) {
							 System.out.printf("Si prega di utilizzare la sintassi dei comandi \n");
						 }
						 else if(!login){
							 System.out.printf("Utente non ha effettuato il login \n");
						 }
						 else if(statusClients==null) {
							System.out.printf("Lista utenti ancora vuota! \n");

						 }
						 else if(statusClients!=null) {
							stampaUtentiOnline();
						 }
						 
						
					 }
						
					// COMANDO CREATEPROJECT
					 else if(arr[0].toUpperCase().equals("CREATEPROJECT")){  // cmd + progetto
						 if(arr.length!=2) {
							 System.out.printf("Si prega di utilizzare la sintassi dei comandi \n");
						 }
						 else if(!login){
							 System.out.printf("Utente non ha effettuato il login\n");
						 }

						 else {
							 	line = line + " " + flagUsername;
							 	 String message = channelCS(line); // ottengo la risposta da parte del server
								 String[] code= message.split(" "); 
						            if(code[0].equals("OK.")) { // risposta positiva server
						            	
						            	System.out.println("La risposta del Server è: " + message.toString());
						            }
						            else { // caso in cui il progetto già esiste
										 System.out.println("La risposta del Server è: " + message.toString());
						            }
						 }

						 
					 }// FINE CREATEPROJECT
						
					//COMANDO PER OTTENERE LA LISTA DEI PROGETTI DI CUI UN UTENTE E' MEMBRO 
					 else if(arr[0].toUpperCase().equals("LISTPROJECTS")) {
						 if(arr.length!=1) {
							 System.out.printf("Si prega di utilizzare la sintassi dei comandi \n");
						 }

						 else if(!login) {
							 System.out.printf("Utente non ha effettuato il login\n");
						 }
						 else { 
							 line = line + " " + flagUsername;
							 String message = channelCS(line); // ottengo la risposta da parte del server
					
						     stampaProgetti(message);

					            
						 }
						 
					 }// FINE COMANDO LISTPROJECT
					
					// COMANDO ADDMEMBER
					 else if(arr[0].toUpperCase().equals("ADDMEMBER")) { // arr = ADDMEMBER + NAMEPROJ + UTENTE DA AGGIUNGERE

						 if(arr.length!=3) {
							 System.out.printf("Si prega di utilizzare la sintassi dei comandi \n");
						 }

						 else if(!login) {
							 System.out.printf("Utente non ha effettuato il login\n");
						 }
						 else { 
							 line = line + " " + flagUsername;
							 String message = channelCS(line); // ottengo la risposta da parte del server
							 String[] code= message.split(" "); 
					            if(code[0].equals("OK.")) { // risposta positiva server
					            	System.out.println("La risposta del Server è: " + message.toString());//	        				
					            }
					            else { // caso in cui il progetto già esiste
									 System.out.println("La risposta del Server è: " + message.toString());

					            }
						 }
					 } // FINE ADDMEMBER
						
					// COMANDO SHOWMEMBERS
					 else if(arr[0].toUpperCase().equals("SHOWMEMBERS")) { // cmd + nomeProgetto 
						 if(arr.length!=2) {
							 System.out.printf("Si prega di utilizzare la sintassi dei comandi \n");
						 }

						 else if(!login) {
							 System.out.printf("Utente non ha effettuato il login\n");
						 }
						 else { 
							 line = line + " " + flagUsername;
							 String message = channelCS(line); // ottengo la risposta da parte del server
				             System.out.println("La risposta del Server è: " + message);//	        				  
						 }
					 } // FINE SHOWMEMBERS
						
					// COMANDO ADDCARD
					 else if(arr[0].toUpperCase().equals("ADDCARD")) { //cmd + nomeProgetto + nomeCard + descrizioneCard
						 if(arr.length!=4) {
							 System.out.printf("Si prega di utilizzare la sintassi dei comandi \n");
						 }

						 else if(!login) {
							 System.out.printf("Utente non ha effettuato il login\n");
						 }
						 else { 
							 line = arr[0] + " " + flagUsername + " " + arr[1] + " " + arr[2]+ " " + arr[3];
							 String message = channelCS(line); // ottengo la risposta da parte del server
							 String[] code= message.split(" "); 
					            if(code[0].equals("OK.")) { // risposta positiva server
					            	System.out.println(message);//	        				
					            }
					            else { // caso in cui il progetto già esiste
									 System.out.println(message);

					            }
						 }
					 } // FINE ADDCARD
						
					
					
					// INIZIO SHOWCARD
					 else if(arr[0].toUpperCase().equals("SHOWCARD")) { //cmd + nameProject + card 
						 if(arr.length!=3) {
							 System.out.printf("Si prega di utilizzare la sintassi dei comandi \n");
						 }

						 else if(!login) {
							 System.out.printf("Utente non ha effettuato il login\n");
						 }
						 else { 
							 line = line + " " + flagUsername;
							 String message = channelCS(line); // ottengo la risposta da parte del server
							 String[] code= message.split(" "); 
					            if(code[0].equals("OK.")) { // risposta positiva server
					            	System.out.println(message);//	        				
					            }
					            else { // caso in cui il progetto già esiste
									 System.out.println(message);

					            }
						 }
					 }// FINE SHOWCARD

						
						// INIZIO SHOWCARDS recuperare tutte le cards di un progetto
					 else if(arr[0].toUpperCase().equals("SHOWCARDS")) { //cmd + nameProject 
						 if(arr.length!=2) {
							 System.out.printf("Si prega di utilizzare la sintassi dei comandi \n");
						 }

						 else if(!login) {
							 System.out.printf("Utente non ha effettuato il login\n");
						 }
						 else { 
							 line = line + " " + flagUsername;
							 String message = channelCS(line); // ottengo la risposta da parte del server
							 String[] code= message.split(" "); 
					            if(code[0].contains("KO.")) { // Se non ci sono card
					            	System.out.println(message);//	        				
					            }
					            else { 
					            	stampaShowCards(message);
					            }
    
						 }
					 }// FINE SHOWCARDS
						
					 // INIZIO MOVECARD
					 else if(arr[0].toUpperCase().equals("MOVECARD")) { //CMD + NAMEPROJ + CARDNAME + LISTPARTENZA + LISTADESTINAZIONE
						String [] move =  line.split(" ",5);
						
						if(move.length!=5) {
							 System.out.printf("Si prega di utilizzare la sintassi dei comandi \n");
						 }

						else if(!login) {
							 System.out.printf("Utente non ha effettuato il login\n");
						 }
						 else { 

							 line = line + " " + flagUsername;
							 String message = channelCS(line); // ottengo la risposta da parte del server
							 String[] code= message.split(" "); 
					            if(code[0].equals("OK.")) { // risposta positiva server
					            	System.out.println(message);//	        				
					            }
					            else { // caso in cui il progetto già esiste
									 System.out.println(message);

					            }
						 }
					 } // FINE MOVECARD
						
					 else if(arr[0].toUpperCase().equals("GETCARDHISTORY")) { //CMD + PROJECTNAME + CARDNAME
						 if(arr.length!=3) {
							 System.out.printf("Si prega di utilizzare la sintassi dei comandi \n");
						 }

						 else if(!login) {
							 System.out.printf("Utente non ha effettuato il login\n");
						 }
						 else { 
							 line = line + " " + flagUsername;
							 String message = channelCS(line); // ottengo la risposta da parte del server					 
							 System.out.println(message);         
						 }
							
						} // FINE GETCARDHISTORY
					
					//COMANDO CANCELPROJECT
					 else if(arr[0].toUpperCase().equals("CANCELPROJECT")) { //CMD + PROJ 
						 if(arr.length!=2) {
							 System.out.printf("Si prega di utilizzare la sintassi dei comandi \n");
						 }

						 else if(!login) {
							 System.out.printf("Utente non ha effettuato il login\n");
						 }
						 else { 
							 line = line + " " + flagUsername;
							 String message = channelCS(line); // ottengo la risposta da parte del server					            
							 System.out.println(message);

					            
						 }
					 } //FINE CANCELPROJECT
						
					 else if(arr[0].toUpperCase().equals("SENDCHATMSG")) { //cmd + project + msg 
						 String [] msg  = line.split(" ",3);
						 if(msg.length!=3) { 
							 System.out.println("Si prega di utilizzare la sintassi dei comandi");
						 }
						 else {
							 String multicastIp =tabIp.get(arr[1].toLowerCase());
	
							 if(multicastIp== null) { 
								 System.out.println("Non c'è alcun indirizzo IP multicast associato a questo progetto"); 
							 } 
							 else{
								
								 InetAddress ia = InetAddress.getByName(multicastIp);
								 byte [ ] data; 
								 DateTimeFormatter h =  DateTimeFormatter.ofPattern("HH:mm:ss");
								 data = ("[" + h.format(LocalDateTime.now())+ "] " + flagUsername + " scrive: " +  msg[2]).getBytes();
								 int port = 5000;
								 DatagramPacket dp = new DatagramPacket(data, data.length,ia,port);
								 MulticastSocket ms = new MulticastSocket();
								 ms.send(dp);
								 System.out.println("Il messaggio è stato inviato!");
								 ms.close();
							 }
						 }
						 
						 
					 }
					 else if(arr[0].toUpperCase().equals("READCHAT")) { // CMD + PRJ
						 if(arr.length!=2) {
							 System.out.printf("Si prega di utilizzare la sintassi dei comandi \n");
						 }
						 
						 else {
						     ArrayList<String> res = chat.get(arr[1].toLowerCase());
						    
						     if(res!= null) {
						    	 synchronized(res) {
						    		 if(res.isEmpty()) {
								    	 System.out.println("Nessun messaggio ricevuto!");
								     }
								     else {
								    		Iterator<String> it = res.iterator();
										     while(it.hasNext()) {
								            	 System.out.println(it.next());
								            	 it.remove();	 
										     }
								     }
						      	}
						     }
						     else { 	 
						      System.out.println("Il progetto selezionato non esiste o non hai i diritti per accedere alla chat ");
						     }
						 }    
						
					 }
						
					 else { // NESSUN COMANDO CORRETTAMENTE SELEZIONATO 
						 System.out.printf("Comando " + arr[0] + " inesistente\n");
					 }
		
				} // fine WHILE	
				
			
			} catch (IOException | NoSuchElementException | NotBoundException e  ) { // eccezione del primo try 
				// TODO Auto-generated catch block
				
				System.out.printf("L'eccezione viene da connect");
				System.exit(0);
			} 
				 System.out.println("Il client ha terminato la sessione\n");
				 System.exit(0);
		}
		
		

	private void closeAllChatThreads() {
		
		Iterator<String> it = tabThreads.keySet().iterator();
		while(it.hasNext()) {
			String s = (String) it.next();
			ThreadMsg t = tabThreads.get(s);
			t.interrupt();
			try {
				t.join();
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
	}

	private String channelCS(String line) {
       
		StringBuilder response = new StringBuilder();
		String message = line;
	         try {
	        	 
	        	buffer.put(message.getBytes());
	            buffer.flip();
	            while(buffer.hasRemaining()) socketChannel.write(buffer);
	            buffer.clear();
	           
	            //Attendo (bloccante) la risposta da parte del server
				socketChannel.read(buffer);
			} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
					System.out.println("Eccezione da read");
				}

	            buffer.flip();	          
	            CharBuffer res = StandardCharsets.UTF_8.decode(buffer);
	            while (res.hasRemaining()) response.append((char)res.get());
	
	            buffer.clear();
		return response.toString();
	}

	
	private void help() {
		System.out.println("\t\t\t\t BENVENUTI IN WORTH");
		System.out.println(" ");
		String leftAlignFormat = "| %-30s | %-60s    |%n";

		System.out.format("+--------------------------------------------------------------------------------------------------+%n");
		System.out.format(leftAlignFormat,"LISTA COMANDI",        "PARAMETRI"       );
		System.out.format("+--------------------------------------------------------------------------------------------------+%n");
		System.out.format(leftAlignFormat,"REGISTER ","NomeUtente + Password");
		System.out.format(leftAlignFormat,"LOGIN ","NomeUtente + Password");
		System.out.format(leftAlignFormat,"LOGOUT ","NomeUtente");
		System.out.format(leftAlignFormat,"LISTUSERS "," ");
		System.out.format(leftAlignFormat,"LISTONLINEUSERS "," ");
		System.out.format(leftAlignFormat,"LISTPROJECTS "," ");
		System.out.format(leftAlignFormat,"CREATEPROJECTS ","NomeProgetto");
		System.out.format(leftAlignFormat,"ADDMEMBER ","NomeProgetto + NomeNuovoUtente");
		System.out.format(leftAlignFormat,"ADDCARD ","NomeProgetto + NomeCard + DescrizioneCard");
		System.out.format(leftAlignFormat,"SHOWMEMBERS ","NomeProgetto");
		System.out.format(leftAlignFormat,"SHOWCARDS ","NomeProgetto");
		System.out.format(leftAlignFormat,"SHOWCARD ","NomeProgetto + NomeCard");
		System.out.format(leftAlignFormat,"MOVECARD ","NomeProgetto + NomeCard + ListaPartenza + ListaDestinazione");
		System.out.format(leftAlignFormat,"GETCARDHISTORY ","NomeProgetto + NomeCard");
		System.out.format(leftAlignFormat,"READCHAT ","NomeProgetto");
		System.out.format(leftAlignFormat,"SENDCHATMSG ","NomeProgetto + Messaggio");
		System.out.format(leftAlignFormat,"CANCELPROJECT ","NomeProgetto");
		System.out.format(leftAlignFormat,"ESC "," ");

		System.out.format("+--------------------------------------------------------------------------------------------------+%n");
	}

	private void stampaUtentiOnline() {
		String leftAlignFormat = "| %-30s | %-15s|%n";
		Iterator<String> iterator = statusClients.keySet().iterator();

	
        
        System.out.format("+-------------------------------------------------+%n");
		System.out.format(leftAlignFormat,"UTENTI",        "STATO"       );
        System.out.format("+-------------------------------------------------+%n");

		 while(iterator.hasNext()) {
			String key= iterator.next();

              if(statusClients.get(key).equals("online")) {
            	  System.out.format(leftAlignFormat, key, "online" );
              }
		 }
	        System.out.format("+-------------------------------------------------+%n");

	}
	
	private void stampaUtenti() {
		String leftAlignFormat = "| %-30s | %-15s|%n";

//		Set<Entry<String, String>> set =statusClients.entrySet();
		Iterator<String> iterator = statusClients.keySet().iterator();
//		Iterator<Entry<String, String>> iterator = set.iterator();
        System.out.println("La lista degli utenti iscritti al servizio è:\n");
        
        System.out.format("+-------------------------------------------------+%n");
		System.out.format(leftAlignFormat,"UTENTI",        "STATO"       );
        System.out.format("+-------------------------------------------------+%n");

		 while(iterator.hasNext()) {
			String key= iterator.next();
       	  	System.out.format(leftAlignFormat, key, statusClients.get(key));

		 }
	     System.out.format("+-------------------------------------------------+%n");

		
	}
	
	private void stampaProgetti(String message) {
		String leftAlignFormat = "|%-20s|%n";
		String [] progetti = message.split("\n");
		System.out.println(message);
		
        System.out.println("La lista dei progetti di "+flagUsername+ " è:");
        System.out.format("+--------------------+%n");
		System.out.format(leftAlignFormat,"PROGETTI " + flagUsername.toUpperCase()    );
        System.out.format("+--------------------+%n");
        for(String progetto: progetti) {
        	System.out.format(leftAlignFormat,progetto );
        }
        System.out.format("+--------------------+%n");
	}
	private void stampaShowCards(String message) {
		
		String leftAlignFormat = "| %-30s| %-30s| %-30s| %n";
		String titleFormat = "| %-30s| %-30s| %-30s | %n";
		
		String [] cards = message.split("\n");
		
        System.out.format("+------------------------------------------------------------------------------------------------+%n");
		System.out.format(titleFormat,"CARDS", "DESCRIZIONE", "LISTA"  );
        System.out.format("+------------------------------------------------------------------------------------------------+%n");
        for(String card: cards) {
        	String [] s = card.split("\t",3);
        	System.out.format(leftAlignFormat,s[0],s[1],s[2]);
	
        }
        System.out.format("+------------------------------------------------------------------------------------------------+%n");
	}
	
	
	
}
