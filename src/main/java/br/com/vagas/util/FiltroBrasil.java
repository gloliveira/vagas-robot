package br.com.vagas.util;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Mantém apenas vagas remotas no Brasil.
 * Descarta presencial, híbrido e vagas no exterior.
 */
public final class FiltroBrasil {

    public static boolean isVagaBrasileira(String... textos) {
        String blob = normalizar(juntar(textos));
        if (blob.isEmpty() || blob.equals("nao informada") || blob.equals("nao informado")) {
            return true;
        }
        boolean brasil = mencionaBrasil(blob);
        boolean estrangeiro = mencionaExterior(blob);
        if (estrangeiro && !brasil) {
            return false;
        }
        if (brasil) {
            return true;
        }
        if (mencionaCidadeOuEstadoBrasileiro(blob)) {
            return true;
        }
        return !estrangeiro;
    }

    public static boolean isPaisBrasil(String pais) {
        String n = normalizar(pais);
        return n.equals("br")
                || n.equals("bra")
                || n.contains("brasil")
                || n.contains("brazil");
    }

    private static boolean mencionaBrasil(String blob) {
        return blob.contains("brasil")
                || blob.contains("brazil")
                || blob.contains("brasileir")
                || blob.matches(".*\\bbr\\b.*");
    }

    private static boolean mencionaCidadeOuEstadoBrasileiro(String blob) {
        String[] locais = {
                "sao paulo", "rio de janeiro", "salvador", "brasilia", "belo horizonte",
                "curitiba", "recife", "fortaleza", "porto alegre", "manaus", "belem",
                "goiania", "campinas", "florianopolis", "vitoria", "natal", "maceio",
                "joao pessoa", "teresina", "cuiaba", "campo grande", "sao luis",
                "aracaju", "palmas", "ribeirao preto", "uberlandia", "joinville",
                "londrina", "niteroi", "osasco", "santo andre", "sao bernardo",
                "jundiai", "santos", "sorocaba", "juiz de fora", "bauru",
                "bahia", "minas gerais", "pernambuco", "parana", "ceara", "amazonas",
                "rio grande do sul", "rio grande do norte", "santa catarina",
                "mato grosso", "espirito santo", "distrito federal", "alagoas",
                "sergipe", "paraiba", "piaui", "maranhao", "tocantins", "rondonia",
                "amapa", "roraima", "goias"
        };
        for (String local : locais) {
            if (blob.contains(local)) {
                return true;
            }
        }
        return false;
    }

    /** País, cidade ou estado brasileiro explícito — não basta "remoto" sem local. */
    public static boolean temSinalBrasil(String... textos) {
        String blob = normalizar(juntar(textos));
        return mencionaBrasil(blob) || mencionaCidadeOuEstadoBrasileiro(blob);
    }

    private static boolean mencionaExterior(String blob) {
        String[] estrangeiros = {
                "united states", "united kingdom", "estados unidos", "eua", "usa",
                "u.s.a", "u.s.", "inglaterra", "england", "london", "londres",
                "india", "bengaluru", "bangalore", "hyderabad", "pune", "chennai",
                "portugal", "lisboa", "lisbon", "porto, portugal",
                "espanha", "spain", "madrid", "barcelona",
                "alemanha", "germany", "berlin", "munich", "munique",
                "franca", "france", "paris",
                "canada", "toronto", "vancouver",
                "mexico", "argentina", "buenos aires", "chile",
                "colombia", "bogota", "peru", "uruguai", "montevideo",
                "netherlands", "holanda", "amsterdam", "poland", "polonia",
                "warsaw", "romania", "bucareste", "ukraine", "israel",
                "australia", "sydney", "ireland", "irlanda", "dublin",
                "italy", "italia", "milan", "milao", "rome", "roma",
                "sweden", "suecia", "switzerland", "suica",
                "worldwide", "anywhere", "anywhere in the world",
                "europe", "europa", "emea", "apac",
                "latin america", "latam", "america latina"
        };
        for (String termo : estrangeiros) {
            if (blob.contains(termo)) {
                return true;
            }
        }
        return blob.matches(".*\\b(uk|uae|ua)\\b.*");
    }

    private static String juntar(String... textos) {
        if (textos == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (String t : textos) {
            if (t != null && !t.isBlank()) {
                if (sb.length() > 0) {
                    sb.append(' ');
                }
                sb.append(t);
            }
        }
        return sb.toString();
    }

    private static String normalizar(String texto) {
        if (texto == null || texto.isBlank()) {
            return "";
        }
        String n = Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        return n.toLowerCase(Locale.ROOT).trim();
    }

    private FiltroBrasil() {}
}
