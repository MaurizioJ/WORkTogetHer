
public class MainClient {

	public static void main(String[] args) {
		
		ClientClass client = new ClientClass(); 
			try{
				client.connect();
				
			}
			catch(Exception e) {
				System.out.println("L'eccezione viene dalla chiusura inaspettata del Client");
				
				e.printStackTrace();

			}

		
	}

}
