import java.io.Serializable;
import java.util.ArrayList;



public class Card implements Serializable{

	String nomeCard; 
	String descrizione; 
	private String list;
    private ArrayList<String> history;
	private static final long serialVersionUID = 1L;

	public Card() {

	}

	public Card(String nomeCard, String descrizione) {
		
		this.nomeCard=nomeCard;
		this.descrizione=descrizione;
		history = new ArrayList<>();
        history.add("TODO");
        list="TODO"; 
	}
	

	

	public String getCard() {
		return nomeCard;
	}
	public void setCard(String nomeCard) {
		this.nomeCard=nomeCard;
	}
	
	public void setDescrizione(String descrizione) {
		this.descrizione=descrizione;
	}
	
	public String getDescrizione() {
		return descrizione;
	}
	
	public void setList(String list) {
		this.list=list.toUpperCase();
	}
	public String getList() {
		return list;
	}
	
	public void addHistory(String stato) {
		history.add(stato.toUpperCase());
	}
	public ArrayList<String> getHistory(){
		return history;
	}
   

}
