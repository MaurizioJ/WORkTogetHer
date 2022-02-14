
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.ServerSocket;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.rmi.RemoteException;
import java.rmi.server.RemoteObject;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

public class ServerClass extends RemoteObject implements ServerInterface{

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private Collection<Utenti> credenziali;
	private ArrayList<Progetti> progetti;
	private ConcurrentHashMap<String,ClientInterface> tabClient;
	private ConcurrentHashMap<String,String> statusForClient;
	private GestoreMulticast gestoreIp;

	
	public ServerClass() throws RemoteException {
		super(); 
		
		progetti = new ArrayList<Progetti>();
		credenziali = Collections.synchronizedCollection(new ArrayList<Utenti>());
		tabClient = new ConcurrentHashMap<String,ClientInterface> ();
		statusForClient = new ConcurrentHashMap<String,String> ();
		gestoreIp= new GestoreMulticast();
	}

	// REGISTRAZIONE dell'utente al servizio 
	public synchronized int register(String nickUtente, String password) throws RemoteException{
		String nickLowerCase = nickUtente.toLowerCase(); // il nome utente verrà sempre inserito minuscolo 
		for(Utenti utente: credenziali) {
			if(utente.getNickUtente().equals(nickLowerCase) ) return -1;//Controllo se già c'è un utente registrato con lo stesso nickname
			
		}
		// Se l'utente cerca di inserire una stringa vuota come nickUtente
		    if(nickUtente.isEmpty()) return -1;
		// Se l'utente cerca di inserire una stringa vuota come password
			if(password.isBlank()) return -1; 
			
		//se soddisfa i requisiti, creo un oggetto utente, lo setto e lo aggiungo all'arraylist
		Utenti utente = new Utenti(nickUtente,password,"offline");
		
		credenziali.add(utente); // inserisco nome utente e password e stato utente nella arrayList
		utente.setStato("offline"); 
		statusForClient.put(nickLowerCase, "offline"); 
		sendStatusClient();	
		/* ora serializzo l'oggetto su file usando NIO*/
		serializzaCredenziali();
		
	return 1;
	}



	public synchronized void sendStatusClient() throws RemoteException {
		//notifico i client del cambiamento di stato
		for(String client: tabClient.keySet() ) {
			
			try {
				// statusForClient viene passata per valore perché non è un oggetto remoto, quindi verranno creati due oggetti (uno sul client e l'altro sul server)
				
				tabClient.get(client).notifyEvent(statusForClient);
				
			} catch (RemoteException e) {
				
				tabClient.remove(client);
			
				for(Utenti utente: credenziali) {
					
					if(utente.getNickUtente().equals(client)) {
						utente.setStato("offline"); 
						statusForClient.replace(client.toLowerCase(), "offline");						
						sendStatusClient();
						return; 
					}
					
				}
				
			}
		
		}
		return;
	}
	
	//registrazione callback per ricevere aggiornamenti da parte del server 
	public synchronized void registerForCallback(String nickUtente, ClientInterface stub) throws RemoteException{
		String nickLowerCase = nickUtente.toLowerCase(); // il nome utente verrà sempre inserito minuscolo nell'arraylist

		if(!tabClient.containsValue(stub)) { 
			tabClient.put(nickLowerCase, stub);
			System.out.println("Il server SALVA i riferimenti dei client : "+ tabClient.keySet());
		}		
	}
	
	//cancellazione callback per non ricevere più aggiornamenti da parte del server
	public synchronized void deleteRegForCallback(String nickUtente) {
		String nickLowerCase = nickUtente.toLowerCase(); // il nome utente verrà sempre inserito minuscolo nell'arraylist

		tabClient.remove(nickLowerCase);
//		System.out.println("Il server CANCELLA i riferimenti dei client : "+ tabClient.keySet());
	}

	

			
	
    // CONNESSIONE TCP 
	public void connectTCP(){
		
		ripristinaStato(); // Implementa la persistenza
			
	    ServerSocketChannel serverChannel; 
	    Selector selector; 
	    try {
	    	serverChannel = ServerSocketChannel.open(); // Ottengo il ServerSocketChannel
			ServerSocket ss = serverChannel.socket();
			InetSocketAddress address = new InetSocketAddress(1919); // connessione su localhost
			ss.bind(address);
			serverChannel.configureBlocking(false);
			selector = Selector.open();
			serverChannel.register(selector, SelectionKey.OP_ACCEPT); //selector monitora operazioni di tipo ACCEPT 
			
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return; 
		}
	    
	    System.out.println("Il server è pronto per ricevere connessioni \n");
	    
	    while(true) {
	    	try {
				selector.select(); // restituisce il numero di canali pronti 
				Set<SelectionKey> canaliPronti = selector.selectedKeys(); // restituisce l'insieme di canali pronti
	        	Iterator <SelectionKey> iterator = canaliPronti.iterator(); // iteratore per scorrere i token dei canali
	        	
	        	while(iterator.hasNext()) {
	        		SelectionKey key = iterator.next();
	        		iterator.remove();
	        	
	        		if(key.isAcceptable())	    acceptConnection(selector,key);
	        		else if(key.isReadable())	readSocketChannel(selector, key);
	        		else if (key.isWritable())	writeSocketChannel(selector,key);
	        		
	        		
	        	}
	    	} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				break; 
			}		
	    }
	}
    
	

	public void writeSocketChannel(Selector selector, SelectionKey selectionKey) {
		// TODO Auto-generated method stub
		SocketChannel client = null; 
		
		try {
			client =(SocketChannel) selectionKey.channel();
			client.configureBlocking(false);
			ByteBuffer buffer = (ByteBuffer) selectionKey.attachment();
			client.write(buffer); // scrivo il contenuto del buffer sul canale e lo mando al client
			buffer.clear(); // reset dei puntatori
			client.register(selector, SelectionKey.OP_READ,buffer);
		} catch (IOException e) {
			e.printStackTrace();
			selectionKey.cancel();
			try {
				client.close();
			} catch (IOException e1) {

				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}


	public String comando(String [] arr, Selector selector, SelectionKey selectionKey) {
		//COMANDO LOGIN 
		
		if(arr[0].toUpperCase().contains("LOGIN")) {	
			synchronized(this) {
				for(Utenti utente: credenziali) {
					// fa il matching delle credenziali presenti in arrayList e  (arr[0] e arr[1]) e controlla lo stato dell'utente
						if(utente.getNickUtente().toLowerCase().equals(arr[1].toLowerCase()) && utente.getPassword().equals(arr[2])) { 
							if(utente.getStato().equals("offline")) {
	//							rispostaToClient = "OK. Accesso eseguito correttamente \n";
								utente.setStato("online");
								statusForClient.replace(utente.getNickUtente().toLowerCase(), "online");
								
									try {
										sendStatusClient();
									} catch (RemoteException e1) {
										// TODO Auto-generated catch block
										e1.printStackTrace();
									}
								 // notifico ai clients il cambiamento di stato
								
								new File("./"+"Credenziali.json").delete();
								/* ora serializzo l'oggetto su file usando NIO*/
								serializzaCredenziali();
								
								
								
								// recupero i progetti del Client e notifico al client l'indirizzo IP multicast
								for(Progetti progetto: progetti) {
									if(progetto.containsMembro(arr[1].toLowerCase())) {
										try {
	//										if(progetto!=null) {
											tabClient.get(arr[1].toLowerCase()).notifyProject(progetto.getnomeProgetto(), progetto.getmulticastIp());
	//										}
										} catch (RemoteException e) {
											// TODO Auto-generated catch block
											e.printStackTrace();
										};
									}
								}
								
								return "OK. Accesso eseguito correttamente \n";
							}
							else {
								return "KO. Utente già collegato! Riprova! \n";
								
							}
						}
				} // fine for 
					
					// utente non presente nell'arrayList 
					return "KO. Accesso negato. Credenziali errate o utente non registrato! Riprova! \n";
						
			}	
			
			
			} // FINE LOGIN
			
		
		//COMANDO LOGOUT 
		else if(arr[0].toUpperCase().contains("LOGOUT")) {
			for(Utenti utente: credenziali) {
				if(utente.getNickUtente().equals(arr[1].toLowerCase()) && utente.getStato().equals("online")) { // utente dichiara di disconnettersi && lo stato è online
					utente.setStato("offline"); 
					statusForClient.replace(utente.getNickUtente().toLowerCase(), "offline");
					
						try {
							sendStatusClient();
						} catch (RemoteException e1) {
							// TODO Auto-generated catch block
							e1.printStackTrace();
						}
						// notifico ai clients il cambiamento di stato
					
					new File("./"+"Credenziali.json").delete();

					/* ora serializzo l'oggetto su file usando NIO*/
					serializzaCredenziali();
					

					return  "OK. Utente disconnesso correttamente! \n";
					
				}

			}
			return "KO. Disconnessione non avvenuta con successo o utente già connesso! \n";

		} //FINE LOGOUT 
		
		// 	COMANDO CREAZIONE PROGETTO
		else if(arr[0].toUpperCase().contains("CREATEPROJECT")) { // cmd + proj + nickUtente
			
			//nel caso in cui non esiste l'arrayList dei progetti
			if(progetti==null) {
				System.out.println("Progetto fallisce perché nullo");
			}
			// controllo se il progetto già esiste 
			for(Progetti prog: progetti) {
				if(prog.getnomeProgetto().equals(arr[1].toLowerCase())){ // il progetto è presente nell'arrayList dei progetti
					return "KO. Progetto già esistente! \n";
				
				}	
			}
			
			 // genera IP 
				String multicastIp=gestoreIp.generaMulticastIp(arr[1].toLowerCase());
				
			
				
				//creo un nuovo progetto 
				// aggiungo al progetto il nome del progetto e l'utente che diventa membro del progetto
				Progetti progetto = new Progetti(arr[1].toLowerCase(),arr[2].toLowerCase(),multicastIp); 
				progetti.add(progetto); // aggiungo il progetto all'arrayList 
				
				try {
					ClientInterface stubClient=tabClient.get(arr[2]);
					stubClient.notifyProject(progetto.getnomeProgetto(), multicastIp);
				} catch (RemoteException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				
				

				
				
				/* ora serializzo l'oggetto su file usando NIO*/
				serializzaProgetti();
				serializzaMulticastIp();
			
				return "OK. Nuovo progetto creato \n";
			
				
		} // FINE CREAZIONE PROGETTO
		
		//COMANDO PER RECUPERARE I PROGETTI 
		else if(arr[0].toUpperCase().contains("LISTPROJECTS")) {
			String nameProgetti = "\n" ;//= "Progetti del membro " + arr[1] + ":\n";
			for(Progetti prog: progetti) {
				if(prog.containsMembro(arr[1].toLowerCase())) { // il membro fa parte del progetto
					nameProgetti += prog.getnomeProgetto() + "\n";
				}
			}
			return nameProgetti;
		}
		
		else if(arr[0].toUpperCase().contains("ADDMEMBER")) { //arr = ADDMEMBER + NAMEPROJ + UTENTE DA AGGIUNGERE + MEMBROPROGETTO
			for(Progetti progetto: progetti) {
				if (progetto.getnomeProgetto().equals(arr[1].toLowerCase()) ) { // il progetto esiste 
					// il membro che aggiunge il nuovo membro deve far parte del progetto, il secondo membro non deve far parte del progetto
					if(progetto.containsMembro(arr[3].toLowerCase())&& !progetto.containsMembro(arr[2].toLowerCase())) { 

						// controllo se l'utente da aggiungere è già iscritto al servizio
						for(Utenti utente: credenziali) {
							if(utente.getNickUtente().equals(arr[2].toLowerCase()) ) {
								//posso aggiungere l'utente come membro del progetto

								progetto.addMembro(arr[2].toLowerCase()); 
								try {
									ClientInterface stubClient=tabClient.get(arr[2].toLowerCase());
									if(stubClient!=null) {
										stubClient.notifyProject(progetto.getnomeProgetto(), progetto.getmulticastIp());
									}
								} catch (RemoteException e) {
									System.out.println("Client non disponibile");
								}
								
								/* ora serializzo l'oggetto su file usando NIO*/
								serializzaProgetti();
								return "OK. Nuovo membro " + arr[2] + " aggiunto correttamente \n" ;
								
							} // fine for utenti
							
						}
						return "KO. Il nuovo utente "+ arr[2] +" non è iscritto al servizio \n";

							 
						
					}
					else {
						return "KO. L'utente fa già parte del progetto \n";
					}
				}
			} // fine for progetti
		return "KO. Il progetto "+ arr[1] +" non esiste. \n";

		} // FINE ADDMEMBER
		
		
		//INIZIO SHOWCARD
		else if(arr[0].toUpperCase().equals("SHOWCARD")) { //cmd + nameProj + nameCard + nickUtente
//			if(progetti== null) progetti = new ArrayList<Progetti>();
			
			for(Progetti progetto: progetti) {

				if(progetto.getnomeProgetto().equals(arr[1].toLowerCase())) {
					if(progetto.containsMembro(arr[3].toLowerCase())) {
						ArrayList<Card> cards = progetto.ottieniCards();
						for(Card card : cards) {
							if(card.getCard().equals(arr[2].toLowerCase())) {
								if(card!=null) {
									return "Il nome della card è " + card.getCard() + "\n" +"La sua descrizione è " + card.getDescrizione() 
									+"\n" + "Il suo stato è "+card.getList()+ "\n" + "La sua storia è " +card.getHistory()+ "\n";
								}
						
							}
						}
						
					}
				}
			}
		return "La card richiesta non è disponibile \n";
			
			
		} // FINE SHOWCARD
		
		//INIZIO SHOWCARDS
		else if(arr[0].toUpperCase().equals("SHOWCARDS")) { //cmd + nomeProj + nickUtente
//			if(progetti==null) {
//				progetti = new ArrayList<Progetti>();
//			}
			
			for(Progetti progetto: progetti) {
				if(progetto.getnomeProgetto().equals(arr[1].toLowerCase())) {
					if(progetto.containsMembro(arr[2].toLowerCase())) {
						ArrayList<Card> cards=progetto.ottieniCards();
						if(cards.isEmpty()) {
							return "KO. Non sono presenti cards nel progetto " + progetto.getnomeProgetto() +"\n";
						}
						
						
//						String rispostaToClient =" Le card del progetto sono: \n" ;
						String rispostaToClient= "";
						for(Card card: cards) {
							rispostaToClient += card.getCard() + "\t" +  card.getDescrizione() + "\t" +card.getList() + "\t \n";
//							rispostaToClient=rispostaToClient.concat("Il nome della card è: " + card.getCard() + ", la sua descrizione è: " + card.getDescrizione() + ", il suo stato è: " + card.getList() +"\n");
						}
						return rispostaToClient;
					}
				}
			}
			return "KO. Non sono presenti progetti con questo nome! \n";
		}// FINE SHOWCARDS
		
		//COMANDO SHOWMEMBERS
		else if(arr[0].toUpperCase().contains("SHOWMEMBERS")) { //cmd + nomeProgetto + nomeMembro
//			if(progetti==null) {
//				progetti = new ArrayList<Progetti>();
//			}
			for(Progetti progetto: progetti) {
				// se presente il progetto e il membro richiedente la lista fa parte del progetto 
				if(progetto.getnomeProgetto().equals(arr[1].toLowerCase()) && progetto.containsMembro(arr[2].toLowerCase())) { 
					return progetto.getMembri().toString();
					
				}
			}
			return "Il progetto non esiste o il membro richiedente la lista non fa parte del progetto \n";
		} // FINE SHOWMEMBERS
		
		//COMANDO ADDCARD
		else if(arr[0].toUpperCase().contains("ADDCARD")) { // cmd + nickUtente + nomeProgetto + nomeCard + descrizioneCard 
	
			for(Progetti progetto: progetti) {
				// se il nome del progetto esiste e contiene il membro
				if(progetto.getnomeProgetto().equals(arr[2].toLowerCase()) && progetto.containsMembro(arr[1].toLowerCase())) {  
					// Controllo che la carta non sia già presente all'interno del progetto
					if(!progetto.containsCard(arr[3].toLowerCase())) {
						progetto.addCard(arr[3].toLowerCase(), arr[4]); // aggiungo e serializzo la card 
						
						/* ora serializzo l'oggetto su file usando NIO*/
						serializzaProgetti();
						
						return "OK. La card è stata aggiunta in questo progetto! \n";
					}
					else {
						return "KO. La card è gia' presente in questo progetto! \n";
					}
				}
				
			} // fine FOR
			return "KO. Il progetto non esiste o il membro non fa parte del progetto \n";			
		}//FINE ADDCARD
		
		//COMANDO MOVECARD
		else if(arr[0].toUpperCase().equals("MOVECARD")){//CMD + NAMEPROJ + CARDNAME + LISTPARTENZA + LISTADESTINAZIONE + nickUtente

			for(Progetti progetto: progetti) {
				if(progetto.getnomeProgetto().equals(arr[1].toLowerCase()) && progetto.containsMembro(arr[5].toLowerCase())){ // nome progetto è uguale all'input del client e controlla se contiene il membro
					if(progetto.containsCard(arr[2].toLowerCase())) { // la card selezionata è presente nel progetto
						if(progetto.controlmoveList(arr[2].toLowerCase(),arr[3].toUpperCase(), arr[4].toUpperCase())){ // controllo se i movimenti di lista sono leciti
							progetto.moveCard(arr[2].toLowerCase(),arr[3].toUpperCase(),arr[4].toUpperCase()); // sposto la card arr[3] da arr[4] a arr[5]
							notifyMoveCard(progetto,arr[2].toLowerCase(),arr[3].toUpperCase(),arr[4].toUpperCase());
							return "OK. La card " +arr[2]+ " è stata spostata dalla lista " + arr[3].toUpperCase() + " alla lista " + arr[4].toUpperCase() + "  \n";
						}
					}
				}
			}
		return "KO. La card non è stata spostata! \n"; 
		} // FINE MOVECARD
		
		//COMANDO GETCARDHISTORY
		else if(arr[0].toUpperCase().equals("GETCARDHISTORY")) { //CMD + PROJECTNAME + CARDNAME + nickUtente
			for(Progetti progetto: progetti) { 
				if(progetto.getnomeProgetto().equals(arr[1].toLowerCase()) && progetto.containsMembro(arr[3].toLowerCase())) { // controllo che il progetto esista && il membro che l'ha richiesto faccia parte del progetto
					if(progetto.containsCard(arr[2].toLowerCase())) {
						Card card= progetto.ottieniCard(arr[2].toLowerCase());
						return "La storia della card è la seguente: " + card.getHistory() + "\n";
					}
				}
			}
			return "La card "+arr[2]+" non è presente nel progetto o il membro non fa parte del progetto " + arr[1] +"\n";
		} // FINE GETCARDHISTORY
		
		//COMANDO CANCELPROJECT
		else if(arr[0].toUpperCase().equals("CANCELPROJECT")) { //CMD + PROJ + MEMBRO
			for(Progetti progetto: progetti) {
				if(progetto.getnomeProgetto().equals(arr[1].toLowerCase()) && progetto.containsMembro(arr[2].toLowerCase())) {
					if(progetto.statoCards()) { // true se tutte le cards si trovano nella lista DONE o non sono presenti cards
//						progetti.remove(progetto);
//						progetti.indexOf(progetto);
						progetti.remove(progetti.indexOf(progetto));
						gestoreIp.removeMulticastIp(progetto.getmulticastIp()); // aggiungo l'indizzo Ip a gestoriMulticast
						for(String membro:progetto.getMembri()) {
							if(statusForClient.get(membro).equals("online")) {
								try {
									tabClient.get(membro).notifyDeleteProject(progetto.getnomeProgetto());
								} catch (RemoteException e) {
									// TODO Auto-generated catch block
									System.out.println("Client non disponibile");
								}
							}
						}
						
						
						new File("./"+"indirizziMulticast.json").delete();
						new File("./"+"Progetti.json").delete();
						deleteDirectory(new File("./"+progetto.getnomeProgetto()));
						
						/* ora serializzo l'oggetto su file usando NIO*/
						serializzaProgetti();
						serializzaMulticastIp();

						return "Il progetto " + arr[1] +" è stato cancellato correttamente "+ "\n";
					}
					else {
						return "Le cards del progetto " + arr[1] + " non si trovano tutte nello stato DONE, quindi il progetto non può essere cancellato \n";
					}
					
				}
			}
			return "Il progetto non è stato cancellato! Controllare di aver digitato correttamente il nome del progetto \n";
			
		} //FINE CANCELPROJECT
		
		//COMANDO ESC
		else if(arr[0].toUpperCase().contains("ESC")) {
	
			selectionKey.cancel();
			
			try {
				selectionKey.channel().close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
//			client.close();
			return "OK. Client disconnesso dal server \n";
		} // FINE COMANDO ESC
		
		return null;
		
		
	}



	private void serializzaMulticastIp() {
		// TODO Auto-generated method stub
		ObjectMapper objectMapper = new ObjectMapper();
		
		String fileMulticast= "indirizziMulticast.json";
		try 	(		
			
				FileChannel outChannelMulticast = FileChannel.open(Paths.get(fileMulticast),StandardOpenOption.WRITE,StandardOpenOption.CREATE);

				)		
		{
			ByteBuffer bufMulticast ;
			bufMulticast = ByteBuffer.wrap(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(gestoreIp)); // wrap un byte di array in un byte buffer
			outChannelMulticast.write(bufMulticast);
			bufMulticast.clear();
            outChannelMulticast.close();
			
			
        }
			
        catch (IOException e) {
        	
            e.printStackTrace(); 
           
        } // fine serializzazione
	}

	private void serializzaProgetti() {
		// TODO Auto-generated method stub
		ObjectMapper objectMapper = new ObjectMapper();
		
		String fileProgetti = "Progetti.json";
		try 	(		
			
				FileChannel outChannel = FileChannel.open(Paths.get(fileProgetti),StandardOpenOption.WRITE,StandardOpenOption.CREATE);

				)		
		{
			ByteBuffer buf ;
			buf = ByteBuffer.wrap(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(progetti)); // wrap un byte di array in un byte buffer
			outChannel.write(buf);
			buf.clear();
            outChannel.close();
			
			
        }
			
        catch (IOException e) {
        	
            e.printStackTrace(); 
           
        } // fine serializzazione
	}

	private void serializzaCredenziali() {
		// TODO Auto-generated method stub
		ObjectMapper objectMapper = new ObjectMapper();
		
		String fileCred = "Credenziali.json";
		
		try 	(		
			
				FileChannel outChannel = FileChannel.open(Paths.get(fileCred),StandardOpenOption.WRITE,StandardOpenOption.CREATE);
				
				)		
		{
			ByteBuffer buffer ; //ByteBuffer.allocateDirect(1024);
		//	buffer.flip();
			buffer = ByteBuffer.wrap(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(credenziali)); // wrap un byte di array in un byte buffer
			outChannel.write(buffer);
			buffer.clear();
            outChannel.close();
			
			
        }
			
        catch (IOException e) {
        	
            e.printStackTrace(); 
           
        }
		
	}

	private void notifyMoveCard(Progetti progetto,String card, String l1, String l2) {

		 String multicastIp =progetto.getmulticastIp();
		

		 if(multicastIp== null) { System.out.println("Non c'è alcun indirizzo IP multicast associato a questo progetto"); } 
		 InetAddress ia;
		try {
			ia = InetAddress.getByName(multicastIp);
		
		 byte [ ] data; 
		 DateTimeFormatter h =  DateTimeFormatter.ofPattern("HH:mm:ss");
		 data = ("[" + h.format(LocalDateTime.now())+ "] " + "Messaggio da Worth: La card " + card + " è stata spostata dalla lista " + l1 + " alla lista " + l2).getBytes();
		 int port = 5000;
		 DatagramPacket dp = new DatagramPacket(data, data.length,ia,port);
//		 DatagramSocket ms = new DatagramSocket();
		 MulticastSocket ms = new MulticastSocket();
		 ms.send(dp);
		 ms.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		 
	}


	public void readSocketChannel(Selector selector, SelectionKey selectionKey) {
		// TODO Auto-generated method stub
		StringBuilder message = new StringBuilder(); 
		SocketChannel client = (SocketChannel) selectionKey.channel();
		
		
		try {
			client.configureBlocking(false);
			ByteBuffer buffer = (ByteBuffer) selectionKey.attachment();
			
			//Leggo dal canale e scrivo nel buffer
			int readBytes= client.read(buffer);
			if(readBytes<0) {
				selectionKey.cancel();
				client.close();
				throw new IOException("Lettura dal canale: FALLITA");
			}
			
			//ora leggo dal buffer finché non saranno esauriti i byte 
			buffer.flip();
			while(buffer.hasRemaining()) message.append((char)buffer.get());
			
			//message è la richiesta ricevuta dal client 
			String [] arr = message.toString().split(" ", 5); // ottengo il comando
			if(arr[0].toUpperCase().equals("MOVECARD")) arr = message.toString().split(" ", 6);

			System.out.println("Messaggio ricevuto dal client \n");
			
			String rispostaToClient= comando(arr,selector, selectionKey);
			
			
			
			/* REGISTRO L'OPERAZIONE DI SCRITTURA */
				//buffer nel quale scrivere la risposta al client e poi nella writeSocketChannel verrà scritta sulla socket 
				ByteBuffer answer = ByteBuffer.allocate(2048);
                answer.put(rispostaToClient.getBytes());
                answer.flip();
				// preparo il buffer alla modalità lettura per la prossima read 
				buffer.flip();
				client.register(selector, SelectionKey.OP_WRITE,buffer);
                selectionKey.attach(answer);

                
			
		} catch (IOException e) {
				selectionKey.cancel();
				try {
					client.close();
				} catch (IOException e2) {
					// TODO Auto-generated catch block
					e2.printStackTrace();

				}

			
		}
		
	}


	public void acceptConnection(Selector selector, SelectionKey key) {
		ServerSocketChannel server = (ServerSocketChannel) key.channel(); // ottengo il canale associato alla chiave
		SocketChannel client;
		try {
			client = server.accept();
			client.configureBlocking(false); // connessione non bloccante
			ByteBuffer byteBuffer = ByteBuffer.wrap(new byte [1024]);
			System.out.println("La connessione con il client è stata accettata" + client.getRemoteAddress() +" \n");
			client.register(selector, SelectionKey.OP_READ,byteBuffer); // ora monitoro operazioni di lettura sul SocketChannel di nome client
			
		} catch (IOException e) {
			key.cancel();
			e.printStackTrace();
		} // Accetto la connessione
		
		
	}
	
	
	@SuppressWarnings({ "resource"})
	private void ripristinaStato() { //DESERIALIZZAZIONE
		
	      File inputFileCred = new File("credenziali.json");
	      File inputFileProg = new File("progetti.json");
	      File inputFileMulticast = new File("indirizziMulticast.json");

	      
	      
	      if(inputFileCred.length()!=0) {
		      try {
	
			    ObjectMapper objectMapper = new ObjectMapper();
			    credenziali = new ArrayList<Utenti>();
				FileChannel channel= new FileInputStream(inputFileCred).getChannel();
				ByteBuffer buffer = ByteBuffer.allocateDirect(2048);
				StringBuilder buf = new StringBuilder();
				
				
				while(channel.read(buffer)!=-1) {
					buffer.flip();
					buf.append(StandardCharsets.UTF_8.decode(buffer)); //decodifico il buffer
					buffer.clear();
				}
				credenziali = objectMapper.readValue(buf.toString(), new TypeReference<ArrayList<Utenti>>() {});

				if (credenziali!=null) { //DA RIVEDERE
					System.out.println("Entra solo se recupera l'oggetto serializzato credenziali ");
                    for (Utenti utente : credenziali) {
                        statusForClient.put(utente.getNickUtente(), "offline");
                        utente.setStato("offline");
                    }
				}
				
				channel.close();
				
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
	      }
	      
	      if(inputFileProg.length()!=0) {
	    	  try {
				    ObjectMapper objectMapper = new ObjectMapper();		

				    progetti = new ArrayList<Progetti>();
					FileChannel channel= new FileInputStream(inputFileProg).getChannel();
					ByteBuffer buffer = ByteBuffer.allocateDirect(2048);
					StringBuilder buf = new StringBuilder();
					
					
					while(channel.read(buffer)!=-1) {
						buffer.flip();
						buf.append(StandardCharsets.UTF_8.decode(buffer)); //decodifico il buffer
						buffer.clear();
					}
			        TypeReference<ArrayList<Progetti>> typeReference = new TypeReference<>() {};

					progetti = objectMapper.readValue(buf.toString(), typeReference);
//				
					for(Progetti progetto: progetti) {
						System.out.println("Chiama RipristinaStatoCard per: " + progetto.getnomeProgetto());
						progetto.ripristinaStatoCard();
					}
					channel.close();
					
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
	      }
	      if(inputFileMulticast.length()!=0) {
	    	  try {
				    ObjectMapper objectMapper = new ObjectMapper();		

					FileChannel channel= new FileInputStream(inputFileMulticast).getChannel();
					ByteBuffer buffer = ByteBuffer.allocateDirect(2048);
					StringBuilder buf = new StringBuilder();
					gestoreIp = new GestoreMulticast();
					
					while(channel.read(buffer)!=-1) {
						buffer.flip();
						buf.append(StandardCharsets.UTF_8.decode(buffer)); //decodifico il buffer
						buffer.clear();
					}
			        TypeReference<GestoreMulticast> typeReference = new TypeReference<>() {};

					gestoreIp = objectMapper.readValue(buf.toString(), typeReference);
					channel.close();
					
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
	      }
	      
	}
	public static boolean deleteDirectory(File path) {
        if(path.exists()) {
         File[] files = path.listFiles();
         for(int i=0; i<files.length; i++) {
               if(files[i].isDirectory()) {
                   deleteDirectory(files[i]);
               }
               else {
                   files[i].delete();
               }
         }
        }
      return(path.delete());
	}
	
}
