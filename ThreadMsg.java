import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;


public class ThreadMsg extends Thread {
//	private String progetto; 
	private String multicastIp;
	private ArrayList<String> arrayMsg ;
//	private Collection<String> synchArrayMsg;
	
	public ThreadMsg( ArrayList<String> arrayMsg, String progetto,String multicastIp) {
//		this.progetto= progetto;
		this.multicastIp=multicastIp;
		this.arrayMsg=arrayMsg;
	}
	
// Si blocca sulla receive e copia i messaggi in un arraylist<string>
	@SuppressWarnings("deprecation")
	public void run() {
		//multicastIp =tabIp.get(arr[1].toLowerCase());
		 if(multicastIp== null) { System.out.println("Non c'è alcun indirizzo IP multicast associato a questo progetto"); } 
		 InetAddress ia;
		 byte [ ] buf = new byte[1024]; 
		 int port = 5000;
		 DatagramPacket dp = new DatagramPacket(buf, buf.length);
		 MulticastSocket ms = null;
		
		while(true) {
			try {
			 ia = InetAddress.getByName(multicastIp);
			 ms = new MulticastSocket(port);
//			 System.out.println("Thread in attesa sulla receive del progetto " + progetto);
			 ms.joinGroup(ia);
			 
			 ms.setSoTimeout(1000);
			 ms.receive(dp);
			 String msg = new String(dp.getData(),0,dp.getLength(),"UTF-8");
			  
			 	synchronized(arrayMsg) {
					arrayMsg.add(msg);
			 	}
			 }
			 catch(SocketTimeoutException e) {
					if(Thread.interrupted()) { 
						System.out.println("Thread che monitora la chat interrotto..."); 
						return ;
					}
				ms.close();
			 }
			 catch (IOException e) {
					// TODO Auto-generated catch block
					System.out.println("Thread interrotto da IOException"); 
					ms.close();
					return ;
					//e.printStackTrace();
				}
		
		 }
			
		
		
		
	}
}
