# Robô de Vagas Java Sênior/Pleno

Um robô em Java que busca vagas de Desenvolvedor Java Sênior e Pleno nas principais plataformas de emprego do Brasil.

## Funcionalidades

- **Busca multicanal**: LinkedIn, Gupy, Vagas.com.br, Programathor, Catho, InfoJobs e Indeed.
- **Filtros**:
  - **Somente vagas remotas no Brasil**. Presencial, híbrido e anúncios no exterior são ignorados.
  - Publicadas há no máximo **14 dias**.
  - Foco em **Java, Spring Boot, JSF, Microserviços**.
- **Relatório HTML** com estatísticas, filtros por plataforma e cards com link para candidatura.
- **Agendamento** (ex.: a cada 6 horas).

## Como executar

É necessário **Java 21** (LTS) e Maven. No Windows com Check Point Harmony SASE, use um JDK completo (Oracle/OpenJDK), não o JRE 11 — só o JDK tem o provider `SunMSCAPI` para confiar na CA do Windows.

```bash
mvn clean package
java -jar target/vagas-robot.jar
```

### Opções

```bash
java -jar target/vagas-robot.jar          # menu interativo
java -jar target/vagas-robot.jar --now    # uma busca e encerra
java -jar target/vagas-robot.jar --auto   # agendado a cada 6 horas
```

## Configuração

Termos, cidade e limite de dias ficam em `src/main/java/br/com/vagas/config/ConfiguracaoBusca.java`. Depois de editar:

```bash
mvn clean package
```

## Relatórios

Cada busca gera um HTML em `relatorios/vagas-java-YYYY-MM-DD_HH-mm.html`.

## Fontes

| Plataforma    | Como é consultada                         | Observação                                      |
|---------------|-------------------------------------------|-------------------------------------------------|
| LinkedIn      | Guest API pública de vagas                | Fonte mais estável                              |
| Gupy          | API JSON do portal (`employability-portal`)| Empresas brasileiras que usam Gupy              |
| Vagas.com.br  | HTML da busca                             | Listagem server-side                            |
| Programathor  | HTML `/jobs-java`                         | Foco em vagas de tecnologia                     |
| Catho         | HTML `article.offer`                      | URL `/vagas/desenvolvedor-java/`                |
| InfoJobs      | HTML `/vagas-de-emprego-....aspx`         | Cards `js_cardLink`                             |
| Indeed        | HTML da busca                             | Costuma bloquear com Cloudflare/CAPTCHA         |

O Indeed protege a busca com desafio anti-bot. O robô tenta coletar, mas se a página vier como "Security Check" a fonte é ignorada naquela execução — as outras plataformas continuam.
