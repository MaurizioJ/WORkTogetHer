
public class Utenti {
private String nickUtente; 
private String password;
private String stato;

	public Utenti() {
		
	}
	public Utenti(String nickUtente, String password, String stato) {
		this.nickUtente=nickUtente;
		this.password=password; 
		this.stato= stato;
	}
	
	public String getNickUtente() {
		return this.nickUtente;
	}
	
	public String getPassword() {
		return this.password;
	}
	public void setStato(String stato) {
		this.stato=stato;
	}
	
	public String getStato() {
		return this.stato;
	}
	

}
