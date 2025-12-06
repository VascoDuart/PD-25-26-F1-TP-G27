import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class ClienteMain extends Application {

    private Stage window;
    private RedeCliente rede;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        this.window = primaryStage;
        this.rede = new RedeCliente();

        window.setTitle("Sistema de Perguntas 2025");
        window.setOnCloseRequest(e -> {
            rede.fechar();
            Platform.exit();
            System.exit(0);
        });

        // Começa no Login
        mostrarLogin();
        window.show();
    }

    public void mostrarLogin() {
        CenaLogin login = new CenaLogin(this, rede);
        window.setScene(new Scene(login.construir(), 400, 500));
    }

    public void mostrarDocente() {
        CenaDocente docente = new CenaDocente(this, rede);
        window.setScene(new Scene(docente.construir(), 600, 600));
    }

    public void mostrarEstudante() {
        CenaEstudante estudante = new CenaEstudante(this, rede);
        window.setScene(new Scene(estudante.construir(), 600, 600));
    }

    // Getters úteis
    public RedeCliente getRede() { return rede; }
}