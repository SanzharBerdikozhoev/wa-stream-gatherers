import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Gatherer;

public class CooccurrenceAnalysisGatherer implements
        Gatherer<String, Map<String, Map<String, Long>>, Map<String, Map<String, Long>>> {
    private final List<String> tokens;
    private final int window;
    private boolean done = false;

    // Speichert Tokens und Fenstergröße für die Analyse.
    public CooccurrenceAnalysisGatherer(List<String> tokens, int window) {
        this.tokens = tokens;
        this.window = window;
    }

    // Liefert den initialen Zustand (leere verschachtelte HashMap).
    @Override
    public Supplier<Map<String, Map<String, Long>>> initializer() {
        return HashMap::new;
    }

    // Liefert den Integrator, der die gesamte Kookkurrenz-Analyse ausführt.
    @Override
    public Integrator<Map<String, Map<String, Long>>, String, Map<String, Map<String, Long>>> integrator() {
        return (state, _, downstream) -> {

            // Führt die Analyse nur einmal aus (Global Aggregation).
            if (!done) {
                done = true;

                // Äußere Schleife: Iteriert über jedes Wort als Zentrumswort.
                for (int i = 0; i < tokens.size(); i++) {
                    String center = tokens.get(i);

                    // Stellt sicher, dass die innere Map für das Zentrumswort existiert.
                    Map<String, Long> centerMap = state.computeIfAbsent(center, k -> new HashMap<>());

                    // Innere Schleife: Iteriert über das Kontextfenster.
                    for (int j = i - window; j <= i + window; j++) {

                        // Prüft Randbedingungen und schließt das Zentrumswort aus (j == i).
                        if (j < 0 || j >= tokens.size() || j == i) continue;

                        String neighbor = tokens.get(j);

                        // Erhöht den Zähler für das Nachbarwort.
                        centerMap.merge(neighbor, 1L, Long::sum);
                    }
                }

                // Schiebt das Endergebnis in den Ausgabestream.
                downstream.push(state);
            }

            // Beendet den Stream nach dem ersten Element, da das Endergebnis gesendet wurde.
            return false;
        };
    }

    // Finisher/Combiner sind für diese globale, aufeinanderfolgende Analyse optional.
}
