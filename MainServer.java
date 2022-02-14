
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;


public class MainServer {

	  public static void main(String[] args) {
	        
		  // RMI 
	        try {
	        	
	        	ServerClass server = new ServerClass(); 
	        	// esporto l'oggetto remoto ServerClass per rendere disponibili i suoi metodi remoti
				ServerInterface stub = (ServerInterface) UnicastRemoteObject.exportObject(server,0); // uso qualsiasi porta per accettare richieste per quel oggetto
				String name = "Server";
				LocateRegistry.createRegistry(5000);
				Registry registry = LocateRegistry.getRegistry(5000);
				registry.rebind(name, stub);
				
				//Avvio la connessione TCP
			    server.connectTCP();
			} catch (RemoteException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
	        
	        
	        
	   }

}
