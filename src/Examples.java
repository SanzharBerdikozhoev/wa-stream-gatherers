import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collector;
import java.util.stream.Gatherer;
import java.util.stream.Stream;

public class Examples {
    // Codebeispiel 1
    public static void example1() {
        var result = Stream.of(5, 2, 8, 3, 1, 4)
                .filter(n -> n % 2 == 0) // Gerade Zahlen herausfiltern
                .map(n -> n * n)         // quadrieren
                .sorted()                       // sortieren
                .toList();                      // Stream in eine Liste umwandeln
        // result => [4, 6, 64]
    }

    public static void example2() {
        var result = Stream.of(1, 19, 63, 27, 27, 3, 1, 89, 89, -10)
                .distinct()       // Zwischenoperation
                .sorted()         // Zwischenoperation
                .skip(1)       // Zwischenoperation
                .limit(2) // Zwischenoperation
                .toList();        // Endoperation

        // result => [1, 3]
    }


    // Codebeispiel 2
    public static List<Integer> findSecondAndThirdSmallestNumber(List<Integer> numbers) {
        // Zwischenoperation = ZWO
        // Endoperation = ENO

        return numbers.stream()   // Erstellen eines Streams
                .distinct()       // ZWO: Entfernen von Duplikaten
                .sorted()         // ZWO: Sortierung in natürlicher Reihenfolge
                .skip(1)       // ZWO: Überspringen vom ersten (kleinsten) Element
                .limit(2) // ZWO: Begrenzung der Listenlänge
                .toList();        // ENO: Transformation des Streams in eine Liste
    }
    // Beispiel: number = List.of(1, 19, 63, 27, 27, 3, 1, 89, 89, -10)
    // Ergebnis: [1, 3]


    // -------------- //
    // Codebeispiel 3 //
    // -------------- //
//    public static List<List<Integer>> slidingWindowAlgorithm(int windowSize, List<Integer> numbers) {
//        return IntStream.range(0, numbers.size() - windowSize + 1)
//                .mapToObj(i -> numbers.subList(i, i + windowSize))
//                .collect(Collectors.toList());
//    }

    // Ein einfacher Collector, der Strings mit einem beliebigen Trennzeichen verbindet.
    // param: delimiter Das Trennzeichen, das zwischen den Elementen eingefügt wird.
    // return: Ein Collector mit T=String, A=StringJoiner, R=String.
    public static Collector<String, StringJoiner, String> stringJoinCollector(String delimiter) {

        // Supplier: Initialisiert den StringJoiner (Akkumulator A)
        // Accumulator: Fügt jedes Stream-Element T hinzu (jetzt als Lambda)
        // Combiner: Fügt parallele Akkumulatoren A zusammen (Methode-Referenz beibehalten)
        // Finisher:    Liefert das finale String-Ergebnis R (jetzt als Lambda)

        return Collector.of(
                () -> new StringJoiner(delimiter), // Supplier,
                StringJoiner::add,                 // Accumulator
                StringJoiner::merge,               // Combiner
                StringJoiner::toString             // Finisher
        );

        // Beispiel:
        // List<String> words = List.of("Apfel", "Banane", "Kirsche");
        // String joinedString = words.stream()
        //                .collect(stringJoinCollector(" und "));
        // System.out.println(joinedString + " sind Obst");
        //
        // Ergebnis: Apfel und Banane und Kirsche sind Obst
    }


    // -------------- //
    // Codebeispiel 4 //
    // -------------- //

    // Demonstriert die Unerwünschte Verwendung eines externen Zustands
    // für die laufende Summe (Scan-Operation) in Java-8.
    //
    // Dieses Verfahren, bei dem ein externer, veränderlicher Zustand (AtomicInteger)
    // verwendet wird, erzeugt unerwünschte Nebeneffekte (Side-Effects) in der
    // Stream-Pipeline. Es widerspricht den funktionalen Prinzipien und kann
    // zu inkonsistenten Ergebnissen führen, insbesondere bei paralleler Ausführung.
    public static List<Integer> calculateRunningSum(List<Integer> numbers) {
        // Unerwünschtes Muster: Nutzung eines externen, veränderlichen Zustands
        AtomicInteger runningSum = new AtomicInteger(0);

        return numbers.stream()
                // .map() wird verwendet, um den Zustand zu aktualisieren
                // und das neue Element auszugeben (Side-Effect).
                .map(runningSum::addAndGet)
                .toList();

        // Beispiel: numbers = List.of(1, 2, 3, 4, 5)
        // Ergebnis: [1, 3, 6, 10, 15]
    }



    // -------------- //
    // Codebeispiel 5 //
    // -------------- //

    //  Erstellt einen Gatherer zur Berechnung des beweglichen Mittelwerts.
    //  Gatherer<T, A, R> Generics:
    //  - T (Eingabetyp): Integer (Das aktuelle Element)
    //  - R (Ausgabetyp): Double (Der berechnete Durchschnitt)
    //  - A (Zustandstyp/Akkumulator): int[] (Das interne Status-Array)
    //  return Ein Gatherer für den Running Average.
    public static Gatherer<Integer, int[], Double> runningAverage() {

        return Gatherer.of(

                // 1. Initializer: Erzeugt den anfänglichen internen Zustand (A).
                // Das int-Array dient als Zustands-Container:
                // state[0]: Speichert die laufende Summe (Summe aller bisherigen Elemente).
                // state[1]: Speichert die Anzahl der bisherigen Elemente (Count).
                () -> new int[2], // Zustand: [0] Summe, [1] Anzahl

                // 2. Integrator: Verarbeitet jedes Element (T) und gibt ein Ergebnis (R) aus.
                // Der Integrator ist der Kern der zustandsbehafteten Logik.
                (state, elem, downstream) -> {

                    // a) Zustand aktualisieren (Mutieren des internen Zustands A):
                    state[0] += elem; // Summe erhöhen
                    state[1]++;       // Anzahl erhöhen

                    // b) Ergebnis berechnen:
                    double currentAverage = state[0] / (double) state[1];

                    // c) Ergebnis an den Downstream senden (Ausgabe R):
                    // Der berechnete Mittelwert wird sofort
                    // in den nächsten Schritt des Streams geschoben.
                    downstream.push(currentAverage);

                    // Fortsetzung der Verarbeitung:
                    return true;
                },

                // Combiner und Finisher sind für diese
                // aufeinanderfolgender Scan-Operation nicht notwendig.
                Gatherer.defaultCombiner(),
                Gatherer.defaultFinisher()
        );
    }

//    public static void exampleOfProgramUsage() {
//        // Lesen von Textdatei
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
//        System.out.println("n-Gramme-Erzeugung Identität: " + nGramsWithoutGatherer.equals(nGramsWithGatherer) + "\n");
//
//        Map<String, Map<String, Long>> cooccurrencesWithoutGatherer = performCooccurrenceAnalysisWithoutGatherer(windowSize, tokens);
//        Map<String, Map<String, Long>> cooccurrencesWithGatherer = performCooccurrenceAnalysisWithGatherer(windowSize, tokens);
//
//        // Überprüfen die Ergebnisse auf Ähnlichkeit.
//        System.out.println("Kookkurrenz-Analyse Identität: " + cooccurrencesWithoutGatherer.equals(cooccurrencesWithGatherer)  + "\n");
//
//        // Finden k (k = 5) häufigste Kookkurrenzen für das Wort "See"
//        Map<String, Long> topCooccurrencies = findTopKCooccurrences(centerWord, k, cooccurrencesWithoutGatherer);
//        System.out.println(topCooccurrencies);
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
}
