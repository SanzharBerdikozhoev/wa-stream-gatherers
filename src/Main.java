import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Gatherers;
import java.util.stream.IntStream;

import static java.util.Map.Entry.comparingByValue;
import static java.util.stream.Collectors.toMap;

public class Main {
    public static List<String> produceNGramsWithoutGatherer(int n, List<String> tokens) {
        // Randfallprüfung: Ungültige n-Größe.
        if (n < 1 || n > tokens.size()) {
            return List.of();
        }

        // Sonderfall für Unigramme (n=1).
        if (n == 1) {
            return tokens;
        }

        // IntStream: Erzeugt einen Stream über alle möglichen Startindizes.
        // mapToObj: Holt die Subliste [i, i+n] und verbindet sie zum n-Gramm-String.
        return IntStream.range(0, tokens.size() - n + 1)
                .mapToObj(i -> String.join(" ", tokens.subList(i, i + n)))
                .toList();
    }

    // param: n Die Größe der zu erzeugenden n-Gramme.
    // param: tokens Die Liste der Eingabetokens.
    // return: Eine Liste von n-Gramm-Strings.
    public static List<String> produceNGramsWithGatherer(int n, List<String> tokens) {
        // Randfallprüfung: Ungültige n-Größe.
        if (n < 1 || n > tokens.size()) {
            return List.of();
        }

        // Sonderfall für Unigramme (n=1).
        if (n == 1) {
            return tokens;
        }

        // Gatherers.windowSliding(n): Die deklarative Zwischenoperation,
        // die den Stream in gleitende Fenster (Listen von Wörtern) umwandelt.
        // map: Verbindet die Liste von Wörtern in jedem Fenster zu einem String.
        return tokens.stream()
                .gather(Gatherers.windowSliding(n))
                .map(w -> String.join(" ", w))
                .toList();
    }

    public static Map<String, Map<String, Long>> performCooccurrenceAnalysisWithoutGatherer(
            int window, List<String> tokens) {

        // Randfallprüfung: Ungültige window-Größe.
        if (window < 1 || window > tokens.size()) {
            return Map.of();
        }


        // boxed: Konvertiert primitive IntStream zu Stream<Integer>
        return IntStream.range(0, tokens.size())
                .boxed()
                .collect(toMap(
                        // Schlüssel Mapper: Der Index i liefert das Zentrumswort.
                        i -> tokens.get(i),
                        // Werte Mapper: Erstellt die Kookkurrenz-Map für das aktuelle Zentrumswort
                        i -> {
                            Map<String, Long> map = new HashMap<>();
                            for (int j = i - window; j <= i + window; j++) {
                                // Prüfung der Array-Grenzen und Ausschließen des Zentrumswortes (j == i)
                                if (j < 0 || j >= tokens.size() || j == i) {
                                    continue;
                                }
                                // Zählt die Häufigkeit der Kookkurrenz
                                map.merge(tokens.get(j), 1L, Long::sum);
                            }
                            return map;
                        },
                        // Merge Function: Kombiniert Value-Maps für identische Keys (Zentrumswörter)
                        (map1, map2) -> {
                            map2.forEach((k, v) -> map1.merge(k, v, Long::sum));
                            return map1;
                        }
                ));
    }

    // Führt die Kookkurrenz-Analyse unter Verwendung eines benutzerdefinierten Stream Gatherers durch.
    public static Map<String, Map<String, Long>> performCooccurrenceAnalysisWithGatherer(
            int window, List<String> tokens) {

        // Randfallprüfung: Ungültige window-Größe.
        if (window < 1 || window > tokens.size()) {
            return Map.of();
        }

        // Der Gatherer kapselt die gesamte globale Analyse und gibt nur das Endergebnis (die Map) aus.
        // findFirst: Extrahiert das einzelne Ergebnis-Element aus dem Stream.
        return tokens.stream()
                .gather(new CooccurrenceAnalysisGatherer(tokens, window))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Gatherer sollte ein Ergebnis liefern."));
    }

    public static Map<String, Long> findTopKCooccurrences(String centerWord, int k, Map<String, Map<String, Long>> cooccurrenceMap) {
        // Überprüft, ob das Schlüsselwort (centerWord) in der Kookkurrenz-Karte vorhanden ist.
        if (!cooccurrenceMap.containsKey(centerWord.toLowerCase())) {
            return Map.of();
        }

        Map<String, Long> cooccurrences = cooccurrenceMap.get(centerWord.toLowerCase());

        return cooccurrences.entrySet().stream()
                // Absteigend nach Häufigkeit (Wert) sortieren
                .sorted(Collections.reverseOrder(comparingByValue()))
                .limit(k) // Nur die Top K behalten
                // Sammle in einer Map, die die Sortierreihenfolge beibehält
                .collect(toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (e1, e2) -> e1,
                        LinkedHashMap::new));
    }

    public static List<String> produceTokensFromText(String text) {
        // 1. Text in Kleinbuchstaben umwandeln
        text = text.toLowerCase();

        // 2. Explizite Definition der Wort-Zeichenklasse
        // [a-z] = Basis-Alphabet, [äöüß] = Deutsche Sonderzeichen
        Pattern wordPattern = Pattern.compile("[a-zäöüß]+");

        // 3. Alle passenden Sequenzen (Wörter) suchen und als Tokens extrahieren
        return wordPattern.matcher(text)
                .results() // Stream der gefundenen Matches
                .map(match -> match.group())
                .toList();
    }

//    public static void measureTimeToExecute(String operationName, Runnable operation) {
//        long startTime = System.nanoTime();
//        operation.run();
//
//        // Umrechnung in Millisekunden (1 ms = 1.000.000 ns)
//        double executionTime = (System.nanoTime() - startTime) / 1_000_000.0;
//
//        System.out.printf("%s: Ausführungszeit - %.4f ms",
//                operationName, executionTime);
//    }

    public static void measureTimeToExecute(
            String operationName, Runnable operation, int warmupRuns, int measureRuns) {

        // 1. Aufwärmen: Code 'warmupRuns'-mal ausführen, um JIT-Effekte zu reduzieren.
        for (int i = 0; i < warmupRuns; i++) {
            operation.run();
        }

        // 2. Messung
        long sumOfNanos = 0;

        for (int i = 0; i < measureRuns; i++) {
            long start = System.nanoTime();     // Startzeit in Nanosekunden erfassen
            operation.run();                    // Code ausführen
            sumOfNanos += (System.nanoTime() - start); // Dauer zu Summe hinzufügen
        }

        // 3. Ergebnisberechnung und -ausgabe
        // Berechnung der durchschnittlichen Zeit in Nanosekunden
        double averageNanos = (double) sumOfNanos / measureRuns;

        // Umrechnung in Millisekunden (1 ms = 1.000.000 ns)
        double averageMillis = averageNanos / 1_000_000.0;

        // Ausgabe mit Formatierung für bessere Lesbarkeit
        System.out.printf("%s: Mittelzeit = %.4f ms (Über %d Messläufe)%n",
                operationName, averageMillis, measureRuns);
    }

    // Liest den gesamten Inhalt einer Textdatei in einen String.
    // param: filePath Pfad zur zu lesenden Datei (z.B. "corpus.txt").
    // return: Der Inhalt der Datei als String, oder ein leerer String im Fehlerfall.
    public static String readTextFileAndSetToString(String filePath) {
        Path path = Paths.get(filePath);
        try {
            // Geben den Inhalt der Textdatei zurück
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            // Wenn die Datei nicht gefunden wird oder ein I/O-Fehler auftritt,
            // wird der Fehler behandelt und ein leere String zurückgegeben
            System.err.println("Fehler beim Lesen der Datei " + filePath + ": " + e.getMessage());
            return "";
        }
    }

    public static void writeToJsonFile(Object data, String fileName) throws IOException {
        Gson gson = new GsonBuilder()
                .setPrettyPrinting()
                .create();

        String json = gson.toJson(data);
        Files.writeString(Path.of(fileName), json);
    }

//    public static void main(String[] args) {
//        String text = readTextFileAndSetToString("src/data/Jules_Verne_Zwanzigtausend_Meilen_unter_dem_Meer.txt");
//
//        // Tokenisierung
//        List<String> tokens = produceTokensFromText(text);
//
//        // Parameter
//        int n = 3;
//        int windowSize = 2;
//        String centerWord = "See";
//        int k = 5;
//
//        List<String> nGramsWithoutGatherer = produceNGramsWithoutGatherer(n, tokens);
//        List<String> nGramsWithGatherer = produceNGramsWithGatherer(n, tokens);
//
//        // Überprüfen die Ergebnisse auf Ähnlichkeit.
//        System.out.println("n-Gramme-Erzeugung Identität: " + nGramsWithoutGatherer.equals(nGramsWithGatherer));
//
//        Map<String, Map<String, Long>> cooccurrencesWithoutGatherer = performCooccurrenceAnalysisWithoutGatherer(windowSize, tokens);
//        Map<String, Map<String, Long>> cooccurrencesWithGatherer = performCooccurrenceAnalysisWithGatherer(windowSize, tokens);
//
//        // Überprüfen die Ergebnisse auf Ähnlichkeit.
//        System.out.println("Kookkurrenz-Analyse Identität: " + cooccurrencesWithoutGatherer.equals(cooccurrencesWithGatherer)  + "\n");
//
//        // Finden k (k = 5) häufigste Kookkurrenzen für das Wort "See"
//        Map<String, Long> topCooccurrencies = findTopKCooccurrences(centerWord, k, cooccurrencesWithoutGatherer);
//        System.out.println(k + " häufigste Kookkurrenzen für das Wort " + centerWord + ": " + topCooccurrencies);
//
//        try {
//            // Speichern der Ergebnisse in Json-Dateien
//            writeToJsonFile(nGramsWithoutGatherer, "src/result/n-Gramme-mit-Gatherer.json");
//            writeToJsonFile(nGramsWithGatherer, "src/result/n-Gramme-ohne-Gatherer.json");
//
//            writeToJsonFile(cooccurrencesWithoutGatherer, "src/result/Cooccurrences-mit-Gatherer.json");
//            writeToJsonFile(cooccurrencesWithGatherer, "src/result/Cooccurrences-Gramme-ohne-Gatherer.json");
//        } catch (IOException e) {
//            // (I/O) Fehlerbehandlung
//            System.out.println("Fehler wurde passiert: " + e.getMessage());
//        }
//    }


    public static void main(String[] args) {
        String text = readTextFileAndSetToString(
                "src/data/Jules_Verne_Zwanzigtausend_Meilen_unter_dem_Meer.txt");
        List<String> tokens = produceTokensFromText(text);

        int warmUpRuns = 5;
        int measureRuns = 10;
        String nGramOp = "n-Gramme";
        String cooccurrenceOp = "Kookkurrenz-Analyse";

        String[] opMethods = new String[]{" (ohne Gatherers) ", " (mit Gatherers) "};
        int[] nParams = new int[]{2, 3, 4, 5};
        int[] windowParams = new int[]{2, 3, 4, 5};

        for (int i = 0; i < nParams.length; i++) {
            int n = nParams[i];

            measureTimeToExecute(
                    nGramOp + opMethods[1] + "(n = " + n + ")",
                    () -> produceNGramsWithGatherer(n, tokens),
                    warmUpRuns, measureRuns);

            measureTimeToExecute(
                    nGramOp + opMethods[0] + "(n = " + n + ")",
                    () -> produceNGramsWithoutGatherer(n, tokens),
                    warmUpRuns, measureRuns);

            System.out.println();
        }

        for (int i = 0; i < windowParams.length; i++) {
            int window = windowParams[i];

            measureTimeToExecute(
                    cooccurrenceOp + opMethods[1] + "(window = " + window + ")",
                    () -> performCooccurrenceAnalysisWithGatherer(window, tokens),
                    warmUpRuns, measureRuns);

            measureTimeToExecute(
                    cooccurrenceOp + opMethods[0] + "(window = " + window + ")",
                    () -> performCooccurrenceAnalysisWithoutGatherer(window, tokens),
                    warmUpRuns, measureRuns);

            System.out.println();
        }
    }
}