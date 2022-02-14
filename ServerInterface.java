import java.rmi.Remote;
import java.rmi.RemoteException;

public interface ServerInterface extends Remote {
	/*Se la registrazione va a buon fine restituisce 1, altrimenti restituisce -1*/
	int register(String nickUtente, String password) throws RemoteException;
	
	/* Registrazione del client al servizio di aggiornamenti, offerto dal server*/
	void registerForCallback(String nickUtente, ClientInterface stub) throws RemoteException;
	
	/* Annulla la registrazione del client al servizio di aggiornamenti, offerto dal server */
    void deleteRegForCallback(String nickUtente) throws RemoteException;

    /* Notifica i client del cambiamento di stato*/
    void sendStatusClient() throws RemoteException;
	
}

