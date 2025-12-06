package pt.isec.pd.tp.estruturas;

import java.io.Serializable;

public class HistoricoItem implements Serializable {
    private static final long serialVersionUID = 1L;

    private String enunciado;
    private String codigo;
    private String dataResposta;
    private String opcaoEscolhida;
    private boolean acertou;

    public HistoricoItem(String enunciado, String codigo, String data, String op, boolean acertou) {
        this.enunciado = enunciado;
        this.codigo = codigo;
        this.dataResposta = data;
        this.opcaoEscolhida = op;
        this.acertou = acertou;
    }

    @Override
    public String toString() {
        String estado = acertou ? "[CERTO]" : "[ERRADO]";
        return String.format("%s | %s | %s (Sua resposta: %s)", estado, codigo, enunciado, opcaoEscolhida);
    }
}