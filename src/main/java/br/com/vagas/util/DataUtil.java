package br.com.vagas.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utilitário para parsing e manipulação de datas de publicação de vagas.
 * Suporta formatos relativos em português e inglês.
 */
public class DataUtil {

    private static final Logger logger = LoggerFactory.getLogger(DataUtil.class);

    // Padrões de data relativa em português
    private static final Pattern HOJE_PT = Pattern.compile("(?i)(hoje|agora|just now|today)");
    private static final Pattern ONTEM_PT = Pattern.compile("(?i)(ontem|yesterday)");
    private static final Pattern HORAS_PT = Pattern.compile("(?i)(\\d+)\\s*(hora|horas|hour|hours|h)");
    private static final Pattern DIAS_PT = Pattern.compile("(?i)(\\d+)\\s*(dia|dias|day|days|d)");
    private static final Pattern SEMANAS_PT = Pattern.compile("(?i)(\\d+)\\s*(semana|semanas|week|weeks|w)");
    private static final Pattern MESES_PT = Pattern.compile("(?i)(\\d+)\\s*(m[eê]s|meses|month|months|mo)");

    private static final Pattern PUBLICADA_EM = Pattern.compile(
            "(?i)(?:publicada|atualizada)\\s+em\\s+(\\d{1,2})/(\\d{1,2})(?:/(\\d{2,4}))?");
    private static final Pattern DIA_MES_ABREV = Pattern.compile(
            "(?i)(\\d{1,2})\\s+(jan|fev|mar|abr|mai|jun|jul|ago|set|out|nov|dez)[a-z]*");
    private static final Pattern ISO_INSTANT = Pattern.compile(
            "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}");

    // Formatos de data absoluta
    private static final DateTimeFormatter[] FORMATTERS = {
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("dd de MMMM de yyyy", new Locale("pt", "BR")),
            DateTimeFormatter.ofPattern("MMMM dd, yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("dd MMM yyyy", new Locale("pt", "BR")),
            DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH)
    };

    /**
     * Converte uma string de data (relativa ou absoluta) para LocalDate.
     * Retorna null se não conseguir fazer o parsing.
     */
    public static LocalDate parsearData(String textoData) {
        if (textoData == null || textoData.isBlank()) {
            return null;
        }

        String bruto = textoData.trim();
        if (bruto.matches("\\d{4}[/.-]\\d{2}[/.-]\\d{2}.*")) {
            try {
                String dataIso = bruto.substring(0, 10).replace('/', '-').replace('.', '-');
                return LocalDate.parse(dataIso);
            } catch (DateTimeParseException ignored) {
                // continua
            }
        }

        String texto = bruto.toLowerCase();

        if (ISO_INSTANT.matcher(textoData.trim()).find()) {
            try {
                String iso = textoData.trim();
                if (iso.endsWith("Z")) {
                    return java.time.Instant.parse(iso).atZone(java.time.ZoneId.systemDefault()).toLocalDate();
                }
                if (iso.length() >= 10) {
                    return LocalDate.parse(iso.substring(0, 10));
                }
            } catch (Exception ignored) {
                // tenta os demais formatos
            }
        }

        // Hoje
        if (HOJE_PT.matcher(texto).find()) {
            return LocalDate.now();
        }

        // Ontem
        if (ONTEM_PT.matcher(texto).find()) {
            return LocalDate.now().minusDays(1);
        }

        // Horas atrás (mesmo dia)
        Matcher mHoras = HORAS_PT.matcher(texto);
        if (mHoras.find()) {
            return LocalDate.now();
        }

        // Dias atrás
        Matcher mDias = DIAS_PT.matcher(texto);
        if (mDias.find()) {
            int dias = Integer.parseInt(mDias.group(1));
            return LocalDate.now().minusDays(dias);
        }

        // Semanas atrás
        Matcher mSemanas = SEMANAS_PT.matcher(texto);
        if (mSemanas.find()) {
            int semanas = Integer.parseInt(mSemanas.group(1));
            return LocalDate.now().minusWeeks(semanas);
        }

        // Meses atrás
        Matcher mMeses = MESES_PT.matcher(texto);
        if (mMeses.find()) {
            int meses = Integer.parseInt(mMeses.group(1));
            return LocalDate.now().minusMonths(meses);
        }

        Matcher mPublicada = PUBLICADA_EM.matcher(texto);
        if (mPublicada.find()) {
            int dia = Integer.parseInt(mPublicada.group(1));
            int mes = Integer.parseInt(mPublicada.group(2));
            int ano = mPublicada.group(3) != null
                    ? normalizarAno(Integer.parseInt(mPublicada.group(3)))
                    : LocalDate.now().getYear();
            return dataComAnoAjustado(dia, mes, ano);
        }

        Matcher mMesAbrev = DIA_MES_ABREV.matcher(texto);
        if (mMesAbrev.find()) {
            int dia = Integer.parseInt(mMesAbrev.group(1));
            int mes = mesAbreviadoParaNumero(mMesAbrev.group(2));
            if (mes > 0) {
                return dataComAnoAjustado(dia, mes, LocalDate.now().getYear());
            }
        }

        // Tentativa de parsing de data absoluta
        for (DateTimeFormatter formatter : FORMATTERS) {
            try {
                return LocalDate.parse(textoData.trim(), formatter);
            } catch (DateTimeParseException ignored) {
                // Tenta o próximo formato
            }
        }

        logger.debug("Não foi possível parsear a data: '{}'", textoData);
        return null;
    }

    /**
     * Verifica se uma data está dentro do período máximo configurado (2 semanas).
     */
    public static boolean isDentroDoLimite(LocalDate data, int maxDias) {
        if (data == null) return true; // Se não souber a data, inclui a vaga
        LocalDate limite = LocalDate.now().minusDays(maxDias);
        return !data.isBefore(limite);
    }

    /**
     * Formata uma data para exibição no relatório.
     */
    public static String formatarParaExibicao(LocalDate data) {
        if (data == null) return "Data não informada";
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        return data.format(fmt);
    }

    private static LocalDate dataComAnoAjustado(int dia, int mes, int ano) {
        try {
            LocalDate data = LocalDate.of(ano, mes, dia);
            if (data.isAfter(LocalDate.now().plusDays(1))) {
                return data.minusYears(1);
            }
            return data;
        } catch (Exception e) {
            return null;
        }
    }

    private static int normalizarAno(int ano) {
        if (ano < 100) {
            return 2000 + ano;
        }
        return ano;
    }

    private static int mesAbreviadoParaNumero(String mes) {
        if (mes == null) return 0;
        switch (mes.toLowerCase().substring(0, Math.min(3, mes.length()))) {
            case "jan": return 1;
            case "fev": return 2;
            case "mar": return 3;
            case "abr": return 4;
            case "mai": return 5;
            case "jun": return 6;
            case "jul": return 7;
            case "ago": return 8;
            case "set": return 9;
            case "out": return 10;
            case "nov": return 11;
            case "dez": return 12;
            default: return 0;
        }
    }

    private DataUtil() {}
}
