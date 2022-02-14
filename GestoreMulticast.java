import java.io.Serializable;
import java.util.ArrayList;

public class GestoreMulticast implements Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	long ip;
	ArrayList<String> ipDelete;
	
	public GestoreMulticast() {
		ipDelete = new ArrayList<String>();
		ip= 0;
	}
	
	public String generaMulticastIp(String progetto) {
		String multicastIp= this.retrieveMulticastIp(); // controllo prima se posso assegnare un indirizzo multicast già generato precedentemente e non utilizzato attualmente
		
		if(multicastIp!=null) return multicastIp;
		
		if(ip>=(256*256*256*16)) return null; // se non ho più indirizzi multicast non disponibili
		
			multicastIp = ip/(256*256*256)+224 + "." + ip/(256*256)%256 + "." + (ip/256)%256 + "." + ip%256;
//			System.out.println(multicastIp);
			ip++;
			return multicastIp;
		
	}
	public long getIp() {
		return this.ip;
	}
	
	public void setIp(long ip) {
		this.ip=ip;
	}
	public void setIpDelete(ArrayList<String> ipDelete) {
		this.ipDelete= ipDelete;
	}
	public ArrayList<String> getIpDelete(){
		return this.ipDelete;
	}
	public String retrieveMulticastIp() {
		if(!ipDelete.isEmpty()) return ipDelete.remove(0);
			
	return null;		
	}
	
	public void removeMulticastIp(String multicastIp) {
		ipDelete.add(multicastIp);
	}

}
