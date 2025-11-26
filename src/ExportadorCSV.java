import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class ExportadorCSV {

    public static void exportar(String nomeFicheiro, Pergunta p, List<RespostaEstudante> respostas) {
        // Usa encoding UTF-8 explicitamente e adiciona BOM para Excel
        try (PrintWriter pw = new PrintWriter(new FileWriter(nomeFicheiro, StandardCharsets.UTF_8))) {
            // Escreve BOM (Byte Order Mark) para o Excel reconhecer UTF-8
            pw.write('\ufeff');

            // Lógica para encontrar a opção correta
            String letraCorreta = "N/A";
            if (p.getOpcoes() != null) {
                for (Opcao o : p.getOpcoes()) {
                    if (o.isCorreta()) {
                        letraCorreta = o.getLetra();
                        break;
                    }
                }
            }

            // Lógica para extrair apenas a data (dia) do início
            // Assumindo formato "YYYY-MM-DD HH:mm" -> pega só na parte antes do espaço
            String dia = p.getInicio().contains(" ") ? p.getInicio().split(" ")[0] : p.getInicio();

            // 1. Cabeçalho da Pergunta
            pw.println("\"dia\";\"hora inicial\";\"hora final\";\"enunciado da pergunta\";\"opção certa\"");
            pw.printf("\"%s\";\"%s\";\"%s\";\"%s\";\"%s\"%n",
                    dia,
                    p.getInicio(),
                    p.getFim(),
                    p.getEnunciado(),
                    letraCorreta
            );

            pw.println();

            // 2. Opções
            pw.println("\"opção\";\"texto da opção\"");
            if (p.getOpcoes() != null) {
                for (Opcao o : p.getOpcoes()) {
                    pw.printf("\"%s\";\"%s\"%n", o.getLetra(), o.getTexto());
                }
            }

            pw.println();

            // 3. Respostas dos Estudantes
            pw.println("\"número de estudante\";\"nome\";\"e-mail\";\"resposta\"");
            if (respostas != null) {
                for (RespostaEstudante r : respostas) {
                    pw.printf("\"%s\";\"%s\";\"%s\";\"%s\"%n",
                            r.getNumEstudante(), r.getNome(), r.getEmail(), r.getOpcaoEscolhida());
                }
            }

            System.out.println("[Exportador] Ficheiro gerado com sucesso: " + nomeFicheiro);

        } catch (IOException e) {
            System.err.println("[Exportador] Erro ao escrever ficheiro: " + e.getMessage());
        }
    }
}