import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.IOException;
import java.util.List;

public class ExportadorCSV {

    public static void exportar(String nomeFicheiro, Pergunta p, List<RespostaEstudante> respostas) {
        try (PrintWriter pw = new PrintWriter(new FileWriter(nomeFicheiro))) {
            // Cabeçalho (Figura 1 do Enunciado)
            pw.println("\"dia\";\"hora inicial\";\"hora final\";\"enunciado da pergunta\";\"opção certa\"");
            
            // Dados da pergunta
            // Nota: Assume que 'p' tem estes getters. Se não tiver, adiciona na classe Pergunta.
            pw.printf("\"%s\";\"%s\";\"%s\";\"%s\";\"%s\"%n",
                    "DATA_AQUI", // Podes extrair da dataInicio
                    p.getInicio(), // String dataInicio
                    p.getFim(),    // String dataFim
                    p.getEnunciado(),
                    "?" // O enunciado não diz para enviar a correta ao cliente, mas para o CSV é preciso
            );
            
            pw.println();
            pw.println("\"opção\";\"texto da opção\"");
            for(Opcao o : p.getOpcoes()) {
                pw.printf("\"%s\";\"%s\"%n", o.getLetra(), o.getTexto());
            }

            pw.println();
            pw.println("\"número de estudante\";\"nome\";\"e-mail\";\"resposta\"");
            
            if (respostas != null) {
                for (RespostaEstudante r : respostas) {
                    pw.printf("\"%s\";\"%s\";\"%s\";\"%s\"%n",
                            r.getNumEstudante(), r.getNome(), r.getEmail(), r.getOpcaoEscolhida());
                }
            }
            
            System.out.println("Ficheiro CSV exportado: " + nomeFicheiro);

        } catch (IOException e) {
            System.err.println("Erro ao exportar CSV: " + e.getMessage());
        }
    }
}