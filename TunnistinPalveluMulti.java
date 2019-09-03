/*
    MultiLI language set identification combined with HeLI language identification
	See: Jauhiainen, Lindén, Jauhiainen 2016: "HeLI, a Word-Based Backoff Method for Language Identification" In Proceedings of the 3rd Workshop on Language Technology for Closely Related Languages, Varieties and Dialects (VarDial)
	See: Jauhiainen, Tommi, Krister Lindén, and Heidi Jauhiainen. "Language set identification in noisy synthetic multilingual documents." International Conference on Intelligent Text Processing and Computational Linguistics. Springer, Cham, 2015.
    Copyright (C) 2019 Tommi Jauhiainen
    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.
    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Affero General Public License for more details.
    You should have received a copy of the GNU Affero General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

import java.io.*;
import java.util.*;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import com.google.common.collect.Table.Cell;
import java.lang.Math.*;
import java.io.*;
import java.net.*;
import java.security.*;

class TunnistinPalveluMulti {

// global table holding the language models for all the languages

	private static Table<String, String, Double> gramDict;
	private static Table<String, String, Double> wordDict;
	
// global variable containing all the languages known by the identifier
	
	private static List<String> kielilista = new ArrayList<String>();

// These variables should be moved to a configuration file
// They set the relative frequency to read from each model file

	private static double usedmonos = 0.0000005;
	private static double usedbis = 0.0000005;
	private static double usedtris = 0.0000005;
	private static double usedquads = 0.0000005;
	private static double usedcinqs = 0.0000005;
	private static double usedsexts = 0.0000005;
	private static double usedwords = 0.0000005;

// this is the penalty value for unseen tokens

	private static double gramsakko = 7.0;

// This is the maximum length of used character n-grams (setting them to 0 gives the same outcome, but the identifier still divides the words)

	private static int maksimingram = 6;
	
	private static int port=8080, maxConnections=0;
	
	public static void main(String[] args) {

// We read the file languagelist which includes list of the languages to be included in the repertoire of the language identifier.

		File file = new File("languagelist");
		BufferedReader reader = null;
		
		try {
			reader = new BufferedReader(new FileReader(file));
			String text = null;
			while ((text = reader.readLine()) != null) {
				kielilista.add(text);
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			try {
				if (reader != null) {
					reader.close();
				}
			} catch (IOException e) {
			}
		}
		
		gramDict = HashBasedTable.create();
		wordDict = HashBasedTable.create();
		
		ListIterator gramiterator = kielilista.listIterator();
		while(gramiterator.hasNext()) {
			Object element = gramiterator.next();
			String kieli = (String) element;

			kayLapi(usedmonos, kieli, "mono");
			kayLapi(usedbis, kieli, "bi");
			kayLapi(usedtris, kieli, "tri");
			kayLapi(usedquads, kieli, "quad");
			kayLapi(usedcinqs, kieli, "cinq");
			kayLapi(usedsexts, kieli, "sext");
			kayLapi(usedwords, kieli, "word");
		}
		System.out.println("Ready to accept queries.");
		doServe();
	}
	
	private static void kayLapi(double usedgrams, String kieli, String tyyppi) {
		Table<String, String, Double> tempDict;
		
		tempDict = HashBasedTable.create();
	
		String seuraava = null;
		String pituustiedosto = null;
	
		if (tyyppi.equals("word")) {
			seuraava = "LanguageModels/" + kieli + ".wordcount";
			pituustiedosto = "LanguageModels/" + kieli + ".words.count";
		}
		else {
			seuraava = "LanguageModels/" + kieli + "-" + tyyppi + ".X";
			pituustiedosto = "LanguageModels/" + kieli + "-" + tyyppi + ".count";
		}
	
		double grampituus = 0;
		double langamount = 0;
	
		File file = new File(pituustiedosto);
		BufferedReader reader = null;
		try {
			reader = new BufferedReader(new FileReader(file));
			String text = null;
			while ((text = reader.readLine()) != null) {
				grampituus = Double.parseDouble(text);
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			try {
				if (reader != null) {
					reader.close();
				}
			} catch (IOException e) {
			}
		}

		file = new File(seuraava);
		reader = null;
		try {
			reader = new BufferedReader(new FileReader(file));

			String text = null;
			while ((text = reader.readLine()) != null) {
				String gram = text;
				
				if (tyyppi.equals("word")) {
					gram = gram.replaceAll(".*[1-90]", "");
					gram = gram.replaceAll("$", " ");
				}
				else {
					gram = text.replaceAll(".*[1-90]=", "");
				}

				int amount = Integer.parseInt(text.replaceAll("[^1-90]", ""));
				
				if (amount/grampituus > usedgrams) {
					tempDict.put(gram, kieli, (double) amount);
					langamount = langamount + (double) amount;
				}
				else {
					break;
				}				
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			try {
				if (reader != null) {
					reader.close();
				}
			} catch (IOException e) {
			}
		}				

		for (Cell<String, String, Double> cell: tempDict.cellSet()){
			double probability = -Math.log10(cell.getValue() / langamount);
			if (tyyppi.equals("word")) {
				wordDict.put(cell.getRowKey(), kieli, probability);
			}
			else {
				gramDict.put(cell.getRowKey(), kieli, probability);
			}
		}
	}

	public static String tunnistaMulti(String mysteryText) throws UnsupportedEncodingException {

// langPercetange = kielikoodi, kielen suhteellinen määrä tekstissä prosenteissa 0-100

		Map<String, Double> langPercentage = new HashMap();

// langScore = kielikoodi, kielen scorejen keskiarvo niissä missä se on voittanut

		Map<String, Double> langScore = new HashMap();

		ListIterator gramiterator = kielilista.listIterator();
		while(gramiterator.hasNext()) {
			Object element = gramiterator.next();
			String kieli = (String) element;
			kieli = kieli.substring(0,3);
			langPercentage.put(kieli, 0.0);
			langScore.put(kieli, gramsakko);
		}

		mysteryText = mysteryText.toLowerCase();
		
		int origLength = mysteryText.length();
		
//koko seuraava replace on turha haravointikäytössä
		mysteryText = mysteryText.replaceAll("[^\\p{L}\\p{M}′'’´ʹािीुूृेैोौंँः् া ি ী ু ূ ৃ ে ৈ ো ৌ।্্্я̄\\u07A6\\u07A7\\u07A8\\u07A9\\u07AA\\u07AB\\u07AC\\u07AD\\u07AE\\u07AF\\u07B0\\u0A81\\u0A82\\u0A83\\u0ABC\\u0ABD\\u0ABE\\u0ABF\\u0AC0\\u0AC1\\u0AC2\\u0AC3\\u0AC4\\u0AC5\\u0AC6\\u0AC7\\u0AC8\\u0AC9\\u0ACA\\u0ACB\\u0ACC\\u0ACD\\u0AD0\\u0AE0\\u0AE1\\u0AE2\\u0AE3\\u0AE4\\u0AE5\\u0AE6\\u0AE7\\u0AE8\\u0AE9\\u0AEA\\u0AEB\\u0AEC\\u0AED\\u0AEE\\u0AEF\\u0AF0\\u0AF1]", " ");

		mysteryText = mysteryText.replaceAll("  *", " ");
		
		mysteryText = mysteryText.replaceAll("^ ", "");
		
		int mysteryLength = mysteryText.length();

		if (mysteryLength < (origLength/2)) {
			return(":xxx");
		}

		String[] mysterySplit = mysteryText.split(" ");

		String mysteryTextMod = "";
		String edellinensana = "";

		for (String ss : mysterySplit) {
			if (!ss.equals(edellinensana)) {
				mysteryTextMod = mysteryTextMod + ss + " ";
			}
			edellinensana = ss;
		}

		mysteryTextMod = mysteryTextMod.replaceAll("  *", " ");
		
		mysteryTextMod = mysteryTextMod.replaceAll("^ ", "");
		
		mysteryTextMod = mysteryTextMod.replaceAll(" $", "");

		mysteryText = mysteryTextMod;
		
		mysteryLength = mysteryText.length();
		
		Set<String> foundLanguages = new HashSet<String>();

		int charCounter = 0;
		boolean thresholdInitiated = false;
		int windowLength = 200;
		int charJump = 20;
		int threshold = 5;
		int thresholdCounter = threshold-1;

		String lastLanguage = "xxx";
		
		if (charCounter > mysteryLength - windowLength - 1) {
			String identification = tunnistaTeksti(mysteryText);
			langPercentage.put(identification, 100.0);
			String kielivalikoima = ":" + identification + " " + mysteryLength + "/" + langPercentage.get(identification);
			return (kielivalikoima);
		}
		
		while (charCounter < mysteryLength - windowLength) {
			String windowText = mysteryText.substring(charCounter,charCounter+windowLength);
			String identification = tunnistaTeksti(windowText);
			langPercentage.put(identification, langPercentage.get(identification) + charJump);
			if (!identification.equals(lastLanguage)) {
				thresholdCounter = thresholdCounter + 1;
				thresholdInitiated = true;
				if (thresholdCounter >= threshold) {
					lastLanguage = identification;
					thresholdCounter = 0;
					foundLanguages.add(identification);
				}
				else if (charCounter == mysteryLength - windowLength - 1) {
					if (thresholdCounter >= 0) {
						lastLanguage = identification;
						thresholdCounter = 0;
						foundLanguages.add(identification);
					}
				}
			}
			if (identification.equals(lastLanguage) && thresholdInitiated) {
				thresholdInitiated = false;
				thresholdCounter = 0;
			}
			charCounter = charCounter + charJump;
		}
		
		String kielivalikoima = "";
		
		double foundLength = 0;
		
		Iterator<String> iterator = foundLanguages.iterator();
		while (iterator.hasNext()) {
			String element = iterator.next();
			if (langPercentage.get(element) < windowLength) {
				langPercentage.put("xxx", langPercentage.get("xxx") + langPercentage.get(element));
				iterator.remove();
			}
		}
		
		if (langPercentage.get("xxx") > 0) {
			foundLanguages.add("xxx");
		}
		
		for (String s : foundLanguages) {
			foundLength = foundLength + langPercentage.get(s);
		}
		
		for (String s : foundLanguages) {
			int prosentti = (int)((langPercentage.get(s)/foundLength)*1000);
			double prosentti2 = prosentti/10;
			kielivalikoima = kielivalikoima + ":" + s + " " + langPercentage.get(s) + "/" + prosentti2;
		}
		
		return (kielivalikoima);
	}

	private static String tunnistaTeksti(String teksti) {

// Kiinan merkkien erottaminen muista merkeistä alkaa
		String teksti2 = "";
		int edellinenolikiinaa = 0;
		int edellinenolispace = 0;
		int cykmerkkimaara = 0;
		
		for (int charlaskuri = 0; charlaskuri < teksti.length(); charlaskuri++){
			char kirjain = teksti.charAt(charlaskuri);
			String setti;
			try {
				setti = Character.UnicodeBlock.of(kirjain).toString();
			}
			catch (Exception e) {
				return("xxx");
			}
			if (setti.startsWith("CJK")) {
				if (edellinenolikiinaa == 0 && edellinenolispace == 0) {
					teksti2 = teksti2 + " ";
				}
				edellinenolikiinaa = 1;
				edellinenolispace = 0;
				cykmerkkimaara++;
			}
			else {
				if (edellinenolikiinaa == 1 && kirjain != ' ') {
					teksti2 = teksti2 + " ";
				}
				if (kirjain == ' ') {
					edellinenolispace = 1;
				}
				else {
					edellinenolispace = 0;
				}
				edellinenolikiinaa = 0;
			}
			teksti2 = teksti2 + kirjain;
		}
		
		teksti = teksti2;
// Kiinan merkkien erottaminen muista merkeistä loppuu
		
		int strLength = teksti.length();
		
		if (strLength < 5) {
			return("xxx");
		}

		String[] sanat = teksti.split(" ");

		List<String> sanalista = new ArrayList<String>();
		
		double sanamaara = 0;
		double uniquesanat = 0;
				
		for (String ss : sanat) {
			if (sanalista.contains(ss)) {
				sanamaara = sanamaara +1 ;
			}
			else {
				sanalista.add(ss);
				uniquesanat = uniquesanat + 1;
				sanamaara = sanamaara +1 ;
			}
		}
		
		double suhde = sanamaara / uniquesanat;
		
		if (suhde > 15) {
			return("xxx");
		}
		
		Map<String, Double> kielipisteet = new HashMap();
		
		ListIterator pisteiterator = kielilista.listIterator();
		while(pisteiterator.hasNext()) {
			Object element = pisteiterator.next();
			String pistekieli = (String) element;
			kielipisteet.put(pistekieli, 0.0);
		}
		
		double monesko = 0;

		for (String sana : sanat) {
			Boolean olisana = false;
			
			Map<String, Double> sanapisteet = new HashMap();
			
			monesko = monesko + 1;
			sana = " " + sana + " ";
			
			if (usedwords < 1) {
				if (wordDict.containsRow(sana)) {
					olisana = true;
					pisteiterator = kielilista.listIterator();
					while(pisteiterator.hasNext()) {
						Object element = pisteiterator.next();
						String pistekieli = (String) element;
						if (wordDict.contains(sana,pistekieli)) {
							sanapisteet.put(pistekieli, wordDict.get(sana,pistekieli));
						}
						else {
							sanapisteet.put(pistekieli, gramsakko);
						}
					}
				}
			}
			
			if (!olisana) {
				pisteiterator = kielilista.listIterator();
				while(pisteiterator.hasNext()) {
					Object element = pisteiterator.next();
					String pistekieli = (String) element;
					sanapisteet.put(pistekieli, 0.0);
				}
			}
			
			int t = maksimingram;
			while (t > 0) {
				if (olisana) {
					break;
				}
				else {
					int pituus = sana.length();
					int x = 0;
					int grammaara = 0;
					if (pituus > (t-1)) {
						while (x < pituus - t + 1) {
							String gram = sana.substring(x,x+t);
							if (gramDict.containsRow(gram)) {
								grammaara = grammaara + 1;
								olisana = true;
								
								pisteiterator = kielilista.listIterator();
								while(pisteiterator.hasNext()) {
									Object element = pisteiterator.next();
									String pistekieli = (String) element;
									if (gramDict.contains(gram,pistekieli)) {
										sanapisteet.put(pistekieli, (sanapisteet.get(pistekieli)+gramDict.get(gram,pistekieli)));
									}
									else {
										sanapisteet.put(pistekieli, (sanapisteet.get(pistekieli)+gramsakko));
									}
								}
							}
							x = x + 1;
						}
					}
					if (olisana) {
						pisteiterator = kielilista.listIterator();
						while(pisteiterator.hasNext()) {
							Object element = pisteiterator.next();
							String pistekieli = (String) element;
							sanapisteet.put(pistekieli, (sanapisteet.get(pistekieli)/grammaara));
						}
					}
				}
				t = t -1 ;
			}
			pisteiterator = kielilista.listIterator();
			while(pisteiterator.hasNext()) {
				Object element = pisteiterator.next();
				String pistekieli = (String) element;
				kielipisteet.put(pistekieli, (kielipisteet.get(pistekieli) + sanapisteet.get(pistekieli)));
			}
		}
		
		String voittaja = "xxx";
		String toinen = "xxx";
		Double pienin = gramsakko + 1;
		Double toiseksipienin = pienin;
		
		pisteiterator = kielilista.listIterator();
		while(pisteiterator.hasNext()) {
			Object element = pisteiterator.next();
			String pistekieli = (String) element;
			kielipisteet.put(pistekieli, (kielipisteet.get(pistekieli)/sanamaara));
			if ((100/strLength*cykmerkkimaara) > 50) {
				if (pistekieli != "jpn" && pistekieli != "zho" && pistekieli != "kor" && pistekieli != "yue" && pistekieli != "lzh" && pistekieli != "wuu" && pistekieli != "gan" && pistekieli != "cmn") {
					kielipisteet.put(pistekieli, (gramsakko + 1));
				}
			}
			if (kielipisteet.get(element) < pienin) {
				voittaja = pistekieli;
				pienin = kielipisteet.get(element);
			}
		}

		return (voittaja.substring(0,3));
	}
	
	private static void doServe() {
		int i=0;

		try{
		  ServerSocket listener = new ServerSocket(port);
		  Socket server;

		  while((i++ < maxConnections) || (maxConnections == 0)){
			doComms connection;

			server = listener.accept();
			doComms conn_c= new doComms(server);
			Thread t = new Thread(conn_c);
			t.start();
		  }
		} catch (IOException ioe) {
		  System.out.println("IOException on socket listen: " + ioe);
		  ioe.printStackTrace();
		}
	}
}

class doComms implements Runnable {
    private Socket server;
    private String line,input;

    doComms(Socket server) {
      this.server=server;
    }

    public void run () {

      input="";

      try {
		BufferedReader in = new BufferedReader(new InputStreamReader(server.getInputStream()));
        PrintStream out = new PrintStream(server.getOutputStream());

        line = in.readLine();

		out.println(TunnistinPalveluMulti.tunnistaMulti(line));

        server.close();
      } catch (IOException ioe) {
        System.out.println("IOException on socket listen: " + ioe);
        ioe.printStackTrace();
      }
    }
}
