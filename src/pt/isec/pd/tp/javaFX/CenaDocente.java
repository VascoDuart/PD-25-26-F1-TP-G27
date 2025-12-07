package pt.isec.pd.tp.javaFX;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import pt.isec.pd.tp.ExportadorCSV;
import pt.isec.pd.tp.estruturas.Docente;
import pt.isec.pd.tp.estruturas.Opcao;
import pt.isec.pd.tp.estruturas.Pergunta;
import pt.isec.pd.tp.estruturas.RespostaEstudante;
import pt.isec.pd.tp.mensagens.*;

import java.util.ArrayList;
import java.util.List;

public class CenaDocente {
    private final ClienteMain app;
    private final RedeCliente rede;

    public CenaDocente(ClienteMain app, RedeCliente rede) {
        this.app = app;
        this.rede = rede;
    }

    public VBox construir() {
        VBox layout = new VBox(15);
        layout.setAlignment(Pos.CENTER);
        layout.setPadding(new Insets(30));

        Label lbl = new Label("Área do Docente");
        lbl.setStyle("-fx-font-size: 24px; -fx-font-weight: bold; -fx-text-fill: #333;");

        String estiloBtn = "-fx-font-size: 14px; -fx-padding: 10 20; -fx-pref-width: 250;";

        Button btnCriar = new Button("1. Criar Pergunta");
        btnCriar.setStyle(estiloBtn + "-fx-base: #4CAF50; -fx-text-fill: white;");

        Button btnListar = new Button("2. Consultar Perguntas");
        btnListar.setStyle(estiloBtn + "-fx-base: #2196F3; -fx-text-fill: white;");

        Button btnEditar = new Button("3. Editar Pergunta");
        btnEditar.setStyle(estiloBtn);

        Button btnEliminar = new Button("4. Eliminar Pergunta");
        btnEliminar.setStyle(estiloBtn + "-fx-base: #FFCDD2; -fx-text-fill: #B71C1C;");

        Button btnStats = new Button("5. Ver Estatísticas (Expiradas)");
        btnStats.setStyle(estiloBtn + "-fx-base: #FF9800; -fx-text-fill: white;");

        Button btnCSV = new Button("6. Exportar Resultados (CSV)");
        btnCSV.setStyle(estiloBtn);

        Button btnPerfil = new Button("7. Editar Perfil");
        btnPerfil.setStyle(estiloBtn);

        Button btnSair = new Button("0. Logout");
        btnSair.setStyle(estiloBtn + "-fx-base: #f44336; -fx-text-fill: white;");



        btnCriar.setOnAction(e -> abrirJanelaCriarPergunta());

        btnListar.setOnAction(e -> {
            String filtro = escolherFiltro();
            if (filtro != null) {

                enviarPedidoLista(filtro, lista -> mostrarJanelaResultados(lista, filtro));
            }
        });

        btnEditar.setOnAction(e -> abrirJanelaEditarPergunta());

        btnEliminar.setOnAction(e -> {
            String codigo = pedirTexto("Eliminar Pergunta", "Introduza o código da pergunta a eliminar:");
            if (codigo != null && !codigo.isEmpty()) {
                acaoEliminar(codigo);
            }
        });

        btnStats.setOnAction(e -> abrirSeletorDeEstatisticas());

        btnCSV.setOnAction(e -> {
            String codigo = pedirTexto("Exportar CSV", "Introduza o código da pergunta:");
            if (codigo != null && !codigo.isEmpty()) {
                acaoExportarCSV(codigo);
            }
        });

        btnPerfil.setOnAction(e -> abrirJanelaEditarPerfil());

        btnSair.setOnAction(e -> app.mostrarLogin());

        layout.getChildren().addAll(lbl, btnCriar, btnListar, btnEditar, btnEliminar, btnStats, btnCSV, btnPerfil, new Separator(), btnSair);
        return layout;
    }


    private void abrirJanelaCriarPergunta() {
        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("Nova Pergunta");

        VBox layout = new VBox(10);
        layout.setPadding(new Insets(20));

        TextField txtEnunc = new TextField(); txtEnunc.setPromptText("Enunciado");
        TextField txtInicio = new TextField("2025-11-25 10:00");
        TextField txtFim = new TextField("2025-11-26 10:00");

        VBox opcoesBox = new VBox(5);
        List<HBox> listaOpcoesUI = new ArrayList<>();

        Button btnAddOp = new Button("+ Opção");
        btnAddOp.setOnAction(e -> {
            HBox linha = new HBox(5);
            TextField t = new TextField(); t.setPromptText("Texto da opção");
            CheckBox c = new CheckBox("Correta?");
            linha.getChildren().addAll(new Label("Op:"), t, c);
            listaOpcoesUI.add(linha);
            opcoesBox.getChildren().add(linha);
        });

        btnAddOp.fire(); btnAddOp.fire();

        Button btnEnviar = new Button("Criar");
        btnEnviar.setStyle("-fx-base: #4CAF50; -fx-text-fill: white;");
        btnEnviar.setOnAction(e -> {
            List<Opcao> ops = new ArrayList<>();
            char letra = 'a';
            for (HBox hb : listaOpcoesUI) {
                TextField t = (TextField) hb.getChildren().get(1);
                CheckBox c = (CheckBox) hb.getChildren().get(2);
                if (!t.getText().isEmpty()) {
                    ops.add(new Opcao(String.valueOf(letra++), t.getText(), c.isSelected()));
                }
            }

            new Thread(() -> {
                try {

                    Object respObj = rede.enviarEReceber(new MsgCriarPergunta(-1, txtEnunc.getText(), txtInicio.getText(), txtFim.getText(), ops));
                    String resp = (String) respObj;
                    Platform.runLater(() -> {
                        mostrarAlerta("Resultado", resp);
                        if (resp.contains("SUCESSO")) dialog.close();
                    });
                } catch (Exception ex) { ex.printStackTrace(); }
            }).start();
        });

        layout.getChildren().addAll(new Label("Enunciado"), txtEnunc, new Label("Início"), txtInicio, new Label("Fim"), txtFim, new Separator(), new Label("Opções:"), opcoesBox, btnAddOp, new Separator(), btnEnviar);
        Scene scene = new Scene(new ScrollPane(layout), 400, 500);
        dialog.setScene(scene);
        dialog.show();
    }


    private String escolherFiltro() {
        List<String> opcoes = new ArrayList<>();
        opcoes.add("TODAS");
        opcoes.add("ATIVAS");
        opcoes.add("FUTURAS");
        opcoes.add("EXPIRADAS");

        ChoiceDialog<String> dialog = new ChoiceDialog<>("TODAS", opcoes);
        dialog.setTitle("Filtrar Perguntas");
        dialog.setHeaderText("Que perguntas quer ver?");
        dialog.setContentText("Selecione o filtro:");
        return dialog.showAndWait().orElse(null);
    }

    private void enviarPedidoLista(String filtro, CallbackLista callback) {
        new Thread(() -> {
            try {

                Object resp = rede.enviarEReceber(new MsgObterPerguntas(filtro));

                if (resp instanceof List) {
                    List<Pergunta> lista = (List<Pergunta>) resp;
                    Platform.runLater(() -> callback.executar(lista));
                } else {
                    String erro = (String) resp;
                    Platform.runLater(() -> mostrarAlerta("Erro", "Resposta inválida: " + erro));
                }
            } catch (Exception ex) { ex.printStackTrace(); }
        }).start();
    }

    private void mostrarJanelaResultados(List<Pergunta> lista, String filtro) {
        Stage stage = new Stage();
        stage.setTitle("Resultados: " + filtro);

        VBox layout = new VBox(10);
        layout.setPadding(new Insets(15));

        ListView<String> listView = new ListView<>();
        if (lista.isEmpty()) listView.getItems().add("Nenhuma pergunta encontrada.");
        else {
            for (Pergunta p : lista) {
                listView.getItems().add(String.format("[%s] %s\n    %s -> %s",
                        p.getCodigoAcesso(), p.getEnunciado(), p.getInicio(), p.getFim()));
            }
        }

        layout.getChildren().addAll(new Label("Filtro: " + filtro), listView);
        stage.setScene(new Scene(layout, 500, 400));
        stage.show();
    }


    private void abrirSeletorDeEstatisticas() {
        enviarPedidoLista("EXPIRADAS", lista -> {
            if (lista.isEmpty()) {
                mostrarAlerta("Info", "Não há perguntas expiradas.");
            } else {
                mostrarJanelaEscolhaStats(lista);
            }
        });
    }

    private void mostrarJanelaEscolhaStats(List<Pergunta> lista) {
        Stage stage = new Stage();
        stage.setTitle("Estatísticas");
        VBox layout = new VBox(10);
        layout.setPadding(new Insets(15));

        ListView<String> listView = new ListView<>();
        for (Pergunta p : lista) listView.getItems().add("[" + p.getCodigoAcesso() + "] " + p.getEnunciado());

        Button btnVer = new Button("Ver Relatório");
        btnVer.setOnAction(e -> {
            String sel = listView.getSelectionModel().getSelectedItem();
            if (sel != null) {
                String codigo = sel.substring(1, sel.indexOf("]"));
                stage.close();
                pedirEstatisticasFinais(codigo);
            }
        });

        layout.getChildren().addAll(new Label("Selecione uma pergunta:"), listView, btnVer);
        stage.setScene(new Scene(layout, 400, 300));
        stage.show();
    }

    private void pedirEstatisticasFinais(String codigo) {
        new Thread(() -> {
            try {
                // USA O MÉTODO SEGURO
                Object resp = rede.enviarEReceber(new MsgObterEstatisticas(codigo));
                String stats = (String) resp;
                Platform.runLater(() -> mostrarAlerta("Relatório: " + codigo, stats));
            } catch (Exception ex) { ex.printStackTrace(); }
        }).start();
    }



    private void abrirJanelaEditarPergunta() {
        String codigo = pedirTexto("Editar Pergunta", "Qual o código da pergunta a editar?");
        if (codigo == null || codigo.isEmpty()) return;

        Stage dialog = new Stage();
        dialog.setTitle("Editar: " + codigo);
        VBox layout = new VBox(10); layout.setPadding(new Insets(20));

        TextField txtEnunc = new TextField(); txtEnunc.setPromptText("Novo Enunciado");
        TextField txtInicio = new TextField("2025-11-25 10:00");
        TextField txtFim = new TextField("2025-11-26 10:00");
        Button btnSalvar = new Button("Salvar Alterações");

        btnSalvar.setOnAction(e -> {
            new Thread(() -> {
                try {
                    Object respObj = rede.enviarEReceber(new MsgEditarPergunta(codigo, txtEnunc.getText(), txtInicio.getText(), txtFim.getText()));
                    String resp = (String) respObj;
                    Platform.runLater(() -> {
                        mostrarAlerta("Edição", resp);
                        if (resp.contains("SUCESSO")) dialog.close();
                    });
                } catch (Exception ex) { ex.printStackTrace(); }
            }).start();
        });

        layout.getChildren().addAll(new Label("Novos Dados para " + codigo), txtEnunc, txtInicio, txtFim, btnSalvar);
        dialog.setScene(new Scene(layout, 350, 250));
        dialog.show();
    }

    private void acaoEliminar(String codigo) {
        new Thread(() -> {
            try {
                Object respObj = rede.enviarEReceber(new MsgEliminarPergunta(codigo));
                String resp = (String) respObj;
                Platform.runLater(() -> mostrarAlerta("Eliminar", resp));
            } catch (Exception ex) { ex.printStackTrace(); }
        }).start();
    }

    private void acaoExportarCSV(String codigo) {
        new Thread(() -> {
            try {

                Object respP = rede.enviarEReceber(new MsgObterPergunta(codigo));

                if (respP instanceof Pergunta) {
                    Pergunta p = (Pergunta) respP;


                    Object respR = rede.enviarEReceber(new MsgObterRespostas(codigo));
                    List<RespostaEstudante> resps = (List<RespostaEstudante>) respR;


                    Platform.runLater(() -> {
                        String nomeFicheiro = "resultados_" + codigo + ".csv";
                        ExportadorCSV.exportar(nomeFicheiro, p, resps);
                        mostrarAlerta("Exportar CSV", "Ficheiro criado com sucesso:\n" + nomeFicheiro);
                    });
                } else {
                    Platform.runLater(() -> mostrarAlerta("Erro", "Pergunta não encontrada ou acesso negado."));
                }
            } catch (Exception ex) { ex.printStackTrace(); }
        }).start();
    }



    private String pedirTexto(String titulo, String mensagem) {
        TextInputDialog td = new TextInputDialog();
        td.setTitle(titulo);
        td.setHeaderText(null);
        td.setContentText(mensagem);
        return td.showAndWait().orElse(null);
    }

    private void mostrarAlerta(String titulo, String msg) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(titulo);
        alert.setHeaderText(null);
        alert.setContentText(msg);
        alert.showAndWait();
    }

    interface CallbackLista {
        void executar(List<Pergunta> lista);
    }

    private void abrirJanelaEditarPerfil() {
        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("Editar Perfil de Docente");

        VBox layout = new VBox(10);
        layout.setPadding(new Insets(20));
        layout.setAlignment(Pos.CENTER);

        // Campos de entrada
        TextField txtNome = new TextField();
        txtNome.setPromptText("Novo Nome");

        PasswordField txtPass = new PasswordField();
        txtPass.setPromptText("Nova Password");


        TextField txtCodDocente = new TextField();
        txtCodDocente.setPromptText("Código Institucional (Manter o atual)");

        Label lblStatus = new Label("");

        Button btnSalvar = new Button("Salvar Alterações");
        btnSalvar.setStyle("-fx-base: #FF9800; -fx-text-fill: white;");

        btnSalvar.setOnAction(e -> {

            String novoNome = txtNome.getText().trim();
            String novaPass = txtPass.getText().trim();


            Docente d = new Docente(novoNome, "dummy@email.com", novaPass);


            new Thread(() -> {
                try {
                    Object respObj = rede.enviarEReceber(new MsgEditarPerfil(d));
                    String resp = (String) respObj;

                    Platform.runLater(() -> {
                        lblStatus.setText(resp);
                        if (resp.contains("SUCESSO")) {
                            mostrarAlerta("Edição de Perfil", "Perfil atualizado com sucesso.");
                            dialog.close();
                        } else {
                            mostrarAlerta("Erro", resp);
                        }
                    });
                } catch (Exception ex) {
                    ex.printStackTrace();
                    Platform.runLater(() -> lblStatus.setText("Erro de comunicação: " + ex.getMessage()));
                }
            }).start();
        });

        layout.getChildren().addAll(
                new Label("EDITAR PERFIL DOCENTE"),
                txtNome,
                txtPass,
                txtCodDocente,
                btnSalvar,
                lblStatus
        );

        Scene scene = new Scene(layout, 350, 300);
        dialog.setScene(scene);
        dialog.show();
    }
}