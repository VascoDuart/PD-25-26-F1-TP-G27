
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.stage.Modality;

import java.util.List;

public class CenaEstudante {
    private final ClienteMain app;
    private final RedeCliente rede;
    private BorderPane layoutPrincipal;

    public CenaEstudante(ClienteMain app, RedeCliente rede) {
        this.app = app;
        this.rede = rede;
    }

    public BorderPane construir() {
        layoutPrincipal = new BorderPane();

        // --- 1. MENU LATERAL (ESQUERDA) ---
        VBox menu = new VBox(10);
        menu.setPadding(new Insets(20));
        menu.setStyle("-fx-background-color: #f4f4f4; -fx-border-color: #ccc; -fx-border-width: 0 1 0 0;");
        menu.setPrefWidth(220);

        Label lblTitulo = new Label("Área do Aluno");
        lblTitulo.setFont(Font.font("Segoe UI", FontWeight.BOLD, 18));
        lblTitulo.setStyle("-fx-text-fill: #333;");

        // Estilo dos botões
        String estiloBtn = "-fx-background-radius: 5; -fx-padding: 10; -fx-font-size: 13px; -fx-base: #e0e0e0;";

        Button btnResponder = criarBotaoMenu("Responder a Pergunta", estiloBtn);
        Button btnHistorico = criarBotaoMenu("Ver Histórico", estiloBtn);

        // NOVO BOTÃO QUE PEDISTE
        Button btnPerfil = criarBotaoMenu("Editar Perfil", estiloBtn);

        Button btnSair = criarBotaoMenu("Logout", estiloBtn + "-fx-base: #ffcccc; -fx-text-fill: #b71c1c;");

        // Ações
        btnResponder.setOnAction(e -> mostrarEcraResponder());
        btnHistorico.setOnAction(e -> mostrarEcraHistorico());

        // Ação do Editar Perfil (Dummy)
        btnPerfil.setOnAction(e -> abrirJanelaEditarPerfilEstudante());

        btnSair.setOnAction(e -> {
            // Tenta avisar o servidor que vai sair
            new Thread(() -> { try { rede.enviar(new MsgLogout()); } catch(Exception ex){} }).start();
            app.mostrarLogin();
        });

        menu.getChildren().addAll(lblTitulo, new Separator(), btnResponder, btnHistorico, btnPerfil, new Separator(), btnSair);
        layoutPrincipal.setLeft(menu);

        // Ecrã inicial ao abrir
        mostrarEcraResponder();

        return layoutPrincipal;
    }

    // ==================================================================================
    // ECRÃ 1: RESPONDER A PERGUNTA
    // ==================================================================================
    private void mostrarEcraResponder() {
        VBox container = new VBox(20);
        container.setPadding(new Insets(40));
        container.setAlignment(Pos.TOP_LEFT);

        Label lblTitle = new Label("Responder a Pergunta");
        lblTitle.setFont(Font.font("Segoe UI", FontWeight.BOLD, 24));
        lblTitle.setStyle("-fx-text-fill: #444;");

        // Área de pesquisa
        HBox searchBox = new HBox(10);
        searchBox.setAlignment(Pos.CENTER_LEFT);

        Label lblCod = new Label("Código:");
        lblCod.setFont(Font.font(14));

        TextField txtCodigo = new TextField();
        txtCodigo.setPromptText("Ex: A1B2C3");
        txtCodigo.setPrefWidth(150);

        Button btnBuscar = new Button("Buscar Pergunta");
        btnBuscar.setDefaultButton(true); // Enter ativa este botão

        // Área onde a pergunta vai aparecer (inicialmente escondida)
        VBox areaPergunta = new VBox(15);
        areaPergunta.setStyle("-fx-background-color: white; -fx-padding: 20; -fx-border-color: #ddd; -fx-border-radius: 5; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.1), 10, 0, 0, 0);");
        areaPergunta.setVisible(false);
        areaPergunta.setMaxWidth(600);

        Label lblEnunciado = new Label();
        lblEnunciado.setStyle("-fx-font-weight: bold; -fx-font-size: 16px; -fx-text-fill: #2c3e50;");
        lblEnunciado.setWrapText(true);

        VBox opcoesGroup = new VBox(10); // Onde ficam os RadioButtons
        ToggleGroup group = new ToggleGroup();

        Button btnSubmeter = new Button("Enviar Resposta");
        btnSubmeter.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 10 20; -fx-cursor: hand;");

        // --- Lógica de Buscar ---
        btnBuscar.setOnAction(e -> {
            String codigo = txtCodigo.getText().trim();
            if (codigo.isEmpty()) {
                mostrarAlerta("Aviso", "Escreva o código da pergunta.");
                return;
            }

            new Thread(() -> {
                try {
                    // USA O MÉTODO SEGURO
                    Object resp = rede.enviarEReceber(new MsgObterPergunta(codigo));

                    Platform.runLater(() -> {
                        if (resp instanceof Pergunta) {
                            Pergunta p = (Pergunta) resp;
                            lblEnunciado.setText(p.getEnunciado());
                            opcoesGroup.getChildren().clear();
                            group.getToggles().clear();

                            // Criar RadioButtons dinamicamente
                            for (Opcao o : p.getOpcoes()) {
                                RadioButton rb = new RadioButton(o.getTexto());
                                rb.setUserData(o.getLetra()); // Guardamos "a", "b" no user data
                                rb.setToggleGroup(group);
                                rb.setFont(Font.font(14));
                                opcoesGroup.getChildren().add(rb);
                            }
                            areaPergunta.setVisible(true);
                            btnSubmeter.setDisable(false);
                        } else {
                            mostrarAlerta("Erro", "Pergunta não encontrada ou código inválido.");
                            areaPergunta.setVisible(false);
                        }
                    });
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }).start();
        });

        // --- Lógica de Submeter ---
        btnSubmeter.setOnAction(e -> {
            if (group.getSelectedToggle() == null) {
                mostrarAlerta("Atenção", "Selecione uma opção!");
                return;
            }
            String letraEscolhida = (String) group.getSelectedToggle().getUserData();
            String codigo = txtCodigo.getText().trim();

            new Thread(() -> {
                try {
                    Object respObj = rede.enviarEReceber(new MsgResponderPergunta(-1, codigo, letraEscolhida));
                    String resposta = (String) respObj;

                    Platform.runLater(() -> {
                        mostrarAlerta("Resultado", resposta);
                        if (resposta.contains("SUCESSO")) {
                            areaPergunta.setVisible(false);
                            txtCodigo.clear();
                        }
                    });
                } catch (Exception ex) { ex.printStackTrace(); }
            }).start();
        });

        searchBox.getChildren().addAll(lblCod, txtCodigo, btnBuscar);
        areaPergunta.getChildren().addAll(lblEnunciado, new Separator(), opcoesGroup, new Separator(), btnSubmeter);
        container.getChildren().addAll(lblTitle, searchBox, areaPergunta);

        layoutPrincipal.setCenter(container);
    }

    // ==================================================================================
    // ECRÃ 2: HISTÓRICO
    // ==================================================================================
    private void mostrarEcraHistorico() {
        VBox container = new VBox(15);
        container.setPadding(new Insets(30));
        container.setAlignment(Pos.TOP_LEFT);

        Label lblTitle = new Label("O Meu Histórico");
        lblTitle.setFont(Font.font("Segoe UI", FontWeight.BOLD, 24));

        ListView<String> listaView = new ListView<>();
        listaView.setStyle("-fx-font-size: 14px;");
        listaView.setPlaceholder(new Label("A carregar dados..."));

        Button btnAtualizar = new Button("Atualizar Lista");

        // Lógica de carregar dados
        Runnable carregarDados = () -> {
            new Thread(() -> {
                try {
                    Object resp = rede.enviarEReceber(new MsgObterHistorico());

                    Platform.runLater(() -> {
                        if (resp instanceof List) {
                            List<HistoricoItem> lista = (List<HistoricoItem>) resp;
                            listaView.getItems().clear();
                            if (lista.isEmpty()) {
                                listaView.setPlaceholder(new Label("Sem histórico de perguntas expiradas."));
                            } else {
                                for (HistoricoItem item : lista) {
                                    // Usa o toString() bonito do HistoricoItem
                                    listaView.getItems().add(item.toString());
                                }
                            }
                        }
                    });
                } catch (Exception ex) { ex.printStackTrace(); }
            }).start();
        };

        btnAtualizar.setOnAction(e -> carregarDados.run());

        // Carrega logo ao abrir o ecrã
        carregarDados.run();

        container.getChildren().addAll(lblTitle, btnAtualizar, listaView);
        layoutPrincipal.setCenter(container);
    }

    // --- Helpers de Interface ---
    private Button criarBotaoMenu(String texto, String estilo) {
        Button btn = new Button(texto);
        btn.setMaxWidth(Double.MAX_VALUE);
        btn.setStyle(estilo);
        return btn;
    }

    private void mostrarAlerta(String titulo, String msg) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(titulo);
        alert.setHeaderText(null);
        alert.setContentText(msg);
        alert.showAndWait();
    }

    private void abrirJanelaEditarPerfilEstudante() {
        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("Editar Perfil de Estudante");

        VBox layout = new VBox(10);
        layout.setPadding(new Insets(20));
        layout.setAlignment(Pos.CENTER);

        TextField txtNome = new TextField();
        txtNome.setPromptText("Novo Nome");

        PasswordField txtPass = new PasswordField();
        txtPass.setPromptText("Nova Password");

        TextField txtNumEstudante = new TextField();
        txtNumEstudante.setPromptText("Novo Nº Estudante (Obrigatório)");

        Label lblStatus = new Label("");

        Button btnSalvar = new Button("Salvar Alterações");
        btnSalvar.setStyle("-fx-base: #4CAF50; -fx-text-fill: white;");

        btnSalvar.setOnAction(e -> {
            String novoNome = txtNome.getText().trim();
            String novaPass = txtPass.getText().trim();
            String novoNum = txtNumEstudante.getText().trim();

            if (novoNum.isEmpty()) {
                lblStatus.setText("ERRO: O Nº Estudante é obrigatório."); return;
            }

            // O backend espera um objeto Estudante. Usamos um email dummy.
            Estudante est = new Estudante(novoNum, novoNome, "dummy@email.com", novaPass);

            // Enviamos o MsgEditarPerfil usando o construtor de Estudante
            new Thread(() -> {
                try {
                    Object respObj = rede.enviarEReceber(new MsgEditarPerfil(est));
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
                new Label("EDITAR PERFIL ESTUDANTE"),
                txtNome,
                txtPass,
                txtNumEstudante,
                btnSalvar,
                lblStatus
        );

        Scene scene = new Scene(layout, 350, 300);
        dialog.setScene(scene);
        dialog.show();
    }
}