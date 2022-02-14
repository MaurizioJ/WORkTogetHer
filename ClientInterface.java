import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.concurrent.ConcurrentHashMap;

public interface ClientInterface extends Remote {
	
	public void notifyEvent(ConcurrentHashMap<String, String> statusForClient) throws RemoteException;
	public void notifyProject(String progetto, String multicast) throws RemoteException;
	public void notifyDeleteProject(String progetto)throws RemoteException;

}
