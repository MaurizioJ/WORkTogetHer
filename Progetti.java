import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import com.fasterxml.jackson.databind.ObjectMapper;


public class Progetti implements Serializable {
    

	private static final long serialVersionUID = 1L;
	private File pathProgetto; 
	private String nomeProgetto; 
	private ArrayList<String> membri;
	private ArrayList<String> nomeCards;
	private ArrayList<Card> todoCards;
	private ArrayList<Card> inprogressCards;
	private ArrayList<Card> toberevisedCards;
	private ArrayList<Card> doneCards;
	private String multicastIp;


	public Progetti() {
		
	}
	
	public Progetti(String nomeProgetto, String nickUtente, String multicastIp) {
		this.nomeProgetto = nomeProgetto;
		this.multicastIp= multicastIp;
		
		//Creazione cartella progetto
        pathProgetto = new File(nomeProgetto);
        if (!pathProgetto.exists()) {
            pathProgetto.mkdir();         
        }
		membri = new ArrayList<String> ();
		membri.add(nickUtente); //aggiungo il nuovo membro al progetti
		nomeCards = new ArrayList<String>();
		todoCards= new ArrayList<Card>();
		inprogressCards= new ArrayList<Card>();
		toberevisedCards= new ArrayList<Card>();
		doneCards= new ArrayList<Card>();	
		
		
	}
	
	public void setmulticastIp(String multicastIp) {
		this.multicastIp=multicastIp;
	}
	
	public String getmulticastIp() {
		return this.multicastIp;
	}
	
	public void ripristinaStatoCard() {

		todoCards= new ArrayList<Card>();
		inprogressCards= new ArrayList<Card>();
		toberevisedCards= new ArrayList<Card>();
		doneCards= new ArrayList<Card>();

		for(String ncard: nomeCards) {
			System.out.println("Il nome del progetto è:" + nomeProgetto + " \n il nome della card è : " + ncard);
		     File inputFileCard = new File(nomeProgetto + "/" + ncard + ".json");

			if(inputFileCard.length()!=0) {
		    	  try {
					    ObjectMapper objectMapper = new ObjectMapper();		

					    Card card = new Card();
					    FileChannel channel= new FileInputStream(inputFileCard).getChannel();
						ByteBuffer buffer = ByteBuffer.allocateDirect(2048);
						StringBuilder buf = new StringBuilder();
						
						
						while(channel.read(buffer)!=-1) {
							buffer.flip();
							buf.append(StandardCharsets.UTF_8.decode(buffer)); //decodifico il buffer
							buffer.clear();
						}
						

						card = objectMapper.readValue(buf.toString(), Card.class);
						

						switch(card.getList()) {
						case "TODO": todoCards.add(card); break;
						case "INPROGRESS": inprogressCards.add(card); break;
						case "TOBEREVISED": toberevisedCards.add(card); break;
						case "DONE": doneCards.add(card); break;

						}
					
						
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
		      }
		}
	}
	public String getnomeProgetto() {
		return nomeProgetto;
		
	}
	public boolean containsMembro(String nickUtente) {
		return membri.contains(nickUtente);
	}
	public ArrayList<String> getMembri() {
		
		return membri;
	}
	
	public void setCard(ArrayList<String> nomeCards) {
	this.nomeCards=nomeCards;
	}    
	public ArrayList<String> getCard() {
	return nomeCards;
	}
	public void addMembro(String nickUtente) {
		membri.add(nickUtente);
	}
	
	public boolean containsCard(String card) {
		if(nomeCards== null) nomeCards = new ArrayList<String>();
		
		return nomeCards.contains(card);
	}
	
	public void addCard(String card, String descrizione) {
			

			Card newCard = new Card(card, descrizione); // creo la card
			if(todoCards==null) todoCards= new ArrayList<Card>();
			nomeCards.add(card); // aggiungo il nome della card alla lista dei nomi delle card associate al progetto
			todoCards.add(newCard); // aggiungo la card con la sua descrizione alla lista associata al progetto
			
			
			
			/* ora serializzo l'oggetto newCard su file usando NIO*/ 
			ObjectMapper objectMapper = new ObjectMapper();
			File cardFile =new File( nomeProgetto + "/" + card + ".json");
			
			
			if(!cardFile.exists()) {
				try {
					cardFile.createNewFile();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			try 	(		
				
					FileChannel outChannel = FileChannel.open(Paths.get(cardFile.toString()),StandardOpenOption.WRITE,StandardOpenOption.CREATE);

					)		
			{
				ByteBuffer buf ;
				//= ByteBuffer.allocateDirect(1024);
			//	buf.flip();
				buf = ByteBuffer.wrap(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(newCard)); // wrap un byte di array in un byte buffer
				outChannel.write(buf);
				buf.clear();
	            outChannel.close();
				
				
	        }
				
	        catch (IOException e) {
	        	
	            e.printStackTrace(); 
	           
	        } // fine serializzazione
			
	}

	public Card ottieniCard(String name) {
        if (!nomeCards.contains(name)) return null;
        for (Card card : todoCards) {
            if (card.getCard().equals(name)) {
                return card;
            }
        }
        for (Card card : inprogressCards) {
            if (card.getCard().equals(name)) {
                return card;
            }
        }
        for (Card card : toberevisedCards) {
            if (card.getCard().equals(name)) {
                return card;
            }
        }
        for (Card card : doneCards) {
            if (card.getCard().equals(name)) {
                return card;
            }
        }
        return null;
    }
	 public ArrayList<Card> ottieniCards() {
		 ArrayList<Card> cards = new ArrayList<Card>();
		 
	        if (nomeCards==null) {
	        	System.err.println("NomeCards non deve essere NULL");
	        	return null;
	        }
	        
	        if (todoCards!=null && !todoCards.isEmpty()) {
	            cards.addAll(todoCards);
	        }
	        if (inprogressCards!=null && !inprogressCards.isEmpty()) {
	            cards.addAll(inprogressCards);
	        }
	        if (toberevisedCards!=null && !toberevisedCards.isEmpty()) {
	            cards.addAll(toberevisedCards);
	        }
	        if (doneCards!=null && !doneCards.isEmpty()) {
	            cards.addAll(doneCards);
	        }
	        return cards;
	   }
	 
	 public boolean controlmoveList(String c,String l1, String l2) {
			Card card = ottieniCard(c);
			if(card==null) return false;
			if(card.getList().equals(l1)) { // controllo che la card selezionata si trovi nella lista di partenza
				if(l1.equals("TODO") && l2.equals("INPROGRESS")) return true;
				if(l1.equals("INPROGRESS") && l2.equals("TOBEREVISED") || l1.equals("INPROGRESS") && l2.equals("DONE"))	return true;
				if(l1.equals("TOBEREVISED") && l2.equals("INPROGRESS") || l1.equals("TOBEREVISED") && l2.equals("DONE"))return true;
			}			
		return false;
		}
	
	 public void moveCard(String c, String l1, String l2) {
		 Card card = ottieniCard(c);
		 if(card==null) return ; 
		 
		if(l1.equals("TODO")) { 
//			if(inprogressCards==null) inprogressCards = new ArrayList<Card>(); 
			inprogressCards.add(card);
			todoCards.remove(card);
			card.setList(l2);
			card.addHistory(l2);
		}
		

		if(l1.equals("INPROGRESS") && l2.equals("TOBEREVISED")) {
//			if(toberevisedCards==null) toberevisedCards = new ArrayList<Card>(); 
			toberevisedCards.add(card);
			inprogressCards.remove(card);
			card.setList(l2);
			card.addHistory(l2);
					
		}
		if(l1.equals("INPROGRESS") && l2.equals("DONE")) {
//			if(doneCards==null) doneCards = new ArrayList<Card>(); 
			doneCards.add(card);
			inprogressCards.remove(card);
			card.setList(l2);
     		card.addHistory(l2);
		}
		if(l1.equals("TOBEREVISED") && l2.equals("INPROGRESS")) {
//			if(inprogressCards==null) inprogressCards = new ArrayList<Card>(); 
		 	inprogressCards.add(card);	
			toberevisedCards.remove(card);
			card.setList(l2);
			card.addHistory(l2);
		}
		if(l1.equals("TOBEREVISED") && l2.equals("DONE")) {
//			if(doneCards==null) doneCards = new ArrayList<Card>(); 
			doneCards.add(card);	
			toberevisedCards.remove(card);
			card.setList(l2);
    		card.addHistory(l2);
		}
		
		/* ora serializzo l'oggetto newCard su file usando NIO*/ 
		ObjectMapper objectMapper = new ObjectMapper();
		File cardFile =new File( nomeProgetto + "/" + c + ".json");
		
		
		if(!cardFile.exists()) {
			try {
				cardFile.createNewFile();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		try 	(		
			
				FileChannel outChannel = FileChannel.open(Paths.get(cardFile.toString()),StandardOpenOption.WRITE,StandardOpenOption.CREATE);

				)		
		{
			ByteBuffer buf ;

			buf = ByteBuffer.wrap(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(card)); // wrap un byte di array in un byte buffer
			outChannel.write(buf);
			buf.clear();
            outChannel.close();
			
			
        }
			
        catch (IOException e) {
        	
            e.printStackTrace(); 
           
        } // fine serializzazione

	 } // fine MOVECARD

	public boolean statoCards() {
		ArrayList<Card> cards =ottieniCards();
		if(cards!=null) { //entra anche se l'arrayList è vuoto
			for(Card card: cards) {
				if(!card.getList().equals("DONE")) return false;
			}
			return true;
		} 
	
	return false;
	}


}