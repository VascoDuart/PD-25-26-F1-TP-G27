package pt.isec.pd.tp.javaFX;


import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import pt.isec.pd.tp.estruturas.Docente;
import pt.isec.pd.tp.estruturas.Estudante;
import pt.isec.pd.tp.mensagens.MsgLogin;
import pt.isec.pd.tp.mensagens.MsgRegisto;

public class CenaLogin {
    private final ClienteMain app;
    private final RedeCliente rede;
    private Label lblStatus;

    public CenaLogin(ClienteMain app, RedeCliente rede) {
        this.app = app;
        this.rede = rede;
    }

    public TabPane construir() {
        TabPane tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);


        Tab tabLogin = new Tab("Login", construirFormLogin());


        Tab tabRegisto = new Tab("Registar", construirFormRegisto());

        tabPane.getTabs().addAll(tabLogin, tabRegisto);
        return tabPane;
    }

    private VBox construirFormLogin() {
        VBox layout = new VBox(10);
        layout.setPadding(new Insets(20));
        layout.setAlignment(Pos.CENTER);

        TextField txtEmail = new TextField(""); txtEmail.setPromptText("Email");
        PasswordField txtPass = new PasswordField(); txtPass.setPromptText("Password");
        txtPass.setText("");

        Button btnLogin = new Button("Entrar");
        Button btnConectar = new Button("1. Conectar ao Servidor");
        lblStatus = new Label("Desconectado");
        lblStatus.setTextFill(Color.RED);


        btnConectar.setOnAction(e -> {
            lblStatus.setText("A procurar...");
            new Thread(() -> {
                try {
                    rede.descobrirServidor("127.0.0.1", 9000);
                    rede.conectarTCP();
                    Platform.runLater(() -> {
                        lblStatus.setText("Conectado!");
                        lblStatus.setTextFill(Color.GREEN);
                    });
                } catch (Exception ex) {
                    Platform.runLater(() -> lblStatus.setText("Erro: " + ex.getMessage()));
                }
            }).start();
        });


        btnLogin.setOnAction(e -> {
            if (lblStatus.getTextFill() == Color.RED) {
                lblStatus.setText("Tens de conectar primeiro!"); return;
            }
            fazerLogin(txtEmail.getText(), txtPass.getText());
        });

        layout.getChildren().addAll(new Label("Bem-vindo"), btnConectar, lblStatus,
                new Label("Email"), txtEmail, new Label("Password"), txtPass, btnLogin);
        return layout;
    }

    private VBox construirFormRegisto() {
        VBox layout = new VBox(10);
        layout.setPadding(new Insets(20));
        layout.setAlignment(Pos.CENTER);

        ComboBox<String> comboTipo = new ComboBox<>();
        comboTipo.getItems().addAll("Docente", "Estudante");
        comboTipo.setValue("Estudante");

        TextField txtNome = new TextField(); txtNome.setPromptText("Nome");
        TextField txtEmail = new TextField(); txtEmail.setPromptText("Email");
        PasswordField txtPass = new PasswordField(); txtPass.setPromptText("Password");
        TextField txtExtra = new TextField(); txtExtra.setPromptText("Nº Estudante ou Código Docente");

        Button btnRegistar = new Button("Criar Conta");

        btnRegistar.setOnAction(e -> fazerRegisto(comboTipo.getValue(), txtNome.getText(), txtEmail.getText(), txtPass.getText(), txtExtra.getText()));

        layout.getChildren().addAll(new Label("Novo Registo"), comboTipo, txtNome, txtEmail, txtPass, new Label("ID/Código:"), txtExtra, btnRegistar);
        return layout;
    }

    private void fazerLogin(String email, String pass) {
        new Thread(() -> {
            try {
                System.out.println("[DEBUG GUI] A enviar login para " + email + "...");
                rede.enviar(new MsgLogin(email, pass));


                Object resposta = rede.enviarEReceber(new MsgLogin(email, pass));
                String texto = (String) resposta;

                System.out.println("[DEBUG GUI] O Servidor respondeu: " + texto); // <--- ISTO É O IMPORTANTE

                Platform.runLater(() -> {

                    String textoLower = texto.toLowerCase();

                    if (textoLower.contains("sucesso") && textoLower.contains("docente")) {
                        System.out.println("[DEBUG GUI] A mudar para ecrã de DOCENTE");
                        app.mostrarDocente();
                    }
                    else if (textoLower.contains("sucesso") && textoLower.contains("estudante")) {
                        System.out.println("[DEBUG GUI] A mudar para ecrã de ESTUDANTE");
                        app.mostrarEstudante();
                    }
                    else {
                        System.out.println("[DEBUG GUI] Login falhou. Mostrando erro.");
                        lblStatus.setText("Erro: " + texto);
                        lblStatus.setTextFill(Color.RED);
                    }
                });
            } catch (Exception ex) {
                System.err.println("[DEBUG GUI] Erro grave: ");
                ex.printStackTrace();
                Platform.runLater(() -> {
                    lblStatus.setText("Erro: " + ex.getMessage());
                    lblStatus.setTextFill(Color.RED);
                });
            }
        }).start();
    }

    private void fazerRegisto(String tipo, String nome, String email, String pass, String extra) {

        if (nome.isEmpty() || email.isEmpty() || pass.isEmpty() || extra.isEmpty()) {
            mostrarAlerta("Erro", "Preencha todos os campos!");
            return;
        }

        new Thread(() -> {
            try {

                Object msg;
                if (tipo == null || tipo.equals("Estudante")) {
                    msg = new MsgRegisto(new Estudante(extra, nome, email, pass));
                } else {
                    msg = new MsgRegisto(new Docente(nome, email, pass), extra);
                }


                System.out.println("[GUI] A enviar registo de " + tipo + "...");
                rede.enviar(msg);


                Object resposta = rede.receber();
                String textoResp = (String) resposta;


                Platform.runLater(() -> mostrarAlerta("Registo", textoResp));

            } catch (Exception ex) {

                System.err.println("[GUI] Erro no registo: " + ex.getMessage());
                ex.printStackTrace();
                Platform.runLater(() -> mostrarAlerta("Erro Crítico", "Falha no registo:\n" + ex.getMessage() + "\n\n(Verifica se clicaste em 'Conectar' primeiro!)"));
            }
        }).start();
    }


    private void mostrarAlerta(String titulo, String mensagem) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(titulo);
        alert.setHeaderText(null);
        alert.setContentText(mensagem);
        alert.showAndWait();
    }
}